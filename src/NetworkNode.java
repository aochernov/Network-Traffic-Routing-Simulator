import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NetworkNode extends Agent {
    static final Object Lock = new Object();
    ArrayList<Integer> Neighbours;
    ArrayList<ArrayList<Integer>> RoutingTable;
    ArrayList<PacketQueue> InQueues;
    ArrayList<PacketQueue> OutQueues;
    ArrayList<NetworkPacket> InnerQueue;
    ArrayList<Integer> HashKeys;
    ArrayList<Integer> PreviousSender;
    ArrayList<ArrayList<Integer>> AvailableThroughput;
    Double gammaLVP;
    Integer maxIterationsLVP;

    void algorithmSetup() {
        switch (Synchronizer.Algorithm) {
            case ECMP -> {
                HashKeys = new ArrayList<>();
                Random r = new Random();
                for (ArrayList<Integer> ignored : RoutingTable) {
                    HashKeys.add(r.nextInt(100));
                }
            }
            case DRILL -> {
                PreviousSender = new ArrayList<>();
                for (ArrayList<Integer> ignored : RoutingTable) {
                    PreviousSender.add(-1);
                }
            }
            case LVP -> {
                gammaLVP = 0.3;
                maxIterationsLVP = 30;
                AvailableThroughput = new ArrayList<>();
                for (ArrayList<Integer> routers : RoutingTable) {
                    ArrayList<Integer> availableThroughput = new ArrayList<>();
                    for (Integer router : routers) {
                        availableThroughput.add(getLink(router).Throughput);
                    }
                    AvailableThroughput.add(availableThroughput);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected  void setup() {
        Object[] args = getArguments();
        Neighbours = (ArrayList<Integer>) args[0];
        RoutingTable = (ArrayList<ArrayList<Integer>>)args[1];
        InQueues = new ArrayList<>();
        OutQueues = new ArrayList<>();
        InnerQueue = new ArrayList<>();
        for (int ignored : Neighbours) {
            InQueues.add(new PacketQueue());
            OutQueues.add(new PacketQueue());
        }
        algorithmSetup();
        synchronized (Lock) {
            int name = Integer.parseInt(getLocalName());
            for (NetworkConnection connection : Synchronizer.Connections) {
                if (name == connection.Sender) {
                    connection.register(this, true);
                }
                else if (name == connection.Receiver) {
                    connection.register(this, false);
                }
            }
        }
        addBehaviour(new NetworkNodeBehaviour(this, TimeUnit.MILLISECONDS.toMillis(10)));
        finishFrame();
    }

    void finishFrame() {
        AID receiver = new AID("sync", AID.ISLOCALNAME);
        ACLMessage message = new ACLMessage(Synchronizer.AGENT_TO_SYNC);
        message.addReceiver(receiver);
        send(message);
    }

    int indexByRouter(int router) {
        for (int i = 0; i < Neighbours.size(); i++) {
            if (Neighbours.get(i) == router) {
                return i;
            }
        }
        return -1;
    }

    int singlePacketRouting(NetworkPacket packet, ArrayList<Integer> possibleRoutes) {
        switch (Synchronizer.Algorithm) {
            case ECMP -> {
                int hash = HashKeys.get(packet.Receiver);
                return possibleRoutes.get(hash % possibleRoutes.size());
            }
            case RPS -> {
                Random r = new Random();
                return possibleRoutes.get(r.nextInt(possibleRoutes.size()));
            }
            case DRILL -> {
                int router;
                int randomRouter;
                Random r = new Random();
                int r1 = possibleRoutes.get(r.nextInt(possibleRoutes.size()));
                int r2 = possibleRoutes.get(r.nextInt(possibleRoutes.size()));
                randomRouter = (getLoad(indexByRouter(r1)) <= getLoad(indexByRouter(r2))) ? r1 : r2;
                int previousRouter = PreviousSender.get(packet.Receiver);
                if (previousRouter == -1) {
                    router = randomRouter;
                } else {
                    router = (getLoad(indexByRouter(randomRouter)) <= getLoad(indexByRouter(previousRouter))) ? randomRouter : previousRouter;
                }
                PreviousSender.set(packet.Receiver, router);
                return router;
            }
            case DeTail -> {
                int router = possibleRoutes.get(0);
                double load = getLoad(indexByRouter(router));
                for (int i = 1; i < possibleRoutes.size(); i++) {
                    int newRouter = possibleRoutes.get(i);
                    double newLoad = getLoad(indexByRouter(newRouter));
                    if (newLoad < load) {
                        router = newRouter;
                        load = newLoad;
                    }
                }
                return router;
            }
            default -> {
                return 0;
            }
        }
    }

    void singlePacketRoute(NetworkPacket packet) {
        ArrayList<Integer> possibleRoutes = RoutingTable.get(packet.Receiver);
        int router;
        if (possibleRoutes.size() == 1) {
            router = possibleRoutes.get(0);
        }
        else {
            router = singlePacketRouting(packet, possibleRoutes);
        }
        int idx = indexByRouter(router);
        OutQueues.get(idx).putPacket(packet);
    }

    void massPacketRouting(ArrayList<ArrayList<NetworkPacket>> packetsByDestination, ArrayList<ArrayList<Integer>> routingTable) {
        switch (Synchronizer.Algorithm) {
            case CLOVE -> {
                for (int k = 0; k < packetsByDestination.size(); k++) {
                    double loadSum = 0;
                    ArrayList<Double> loads = new ArrayList<>();
                    for (int router : routingTable.get(k)) {
                        NetworkLink link = getLink(router);
                        double load = link.Throughput - link.getEnqueuedTasks();
                        loads.add(load);
                        loadSum += load;
                    }
                    ArrayList<Integer> roundRobin;
                    if (loadSum == 0.0) {
                        roundRobin = new ArrayList<>(Collections.nCopies(routingTable.get(k).size(), 1));
                    } else {
                        roundRobin = new ArrayList<>();
                        for (double load : loads) {
                            roundRobin.add((int) (Math.ceil(0.5 * packetsByDestination.get(k).size() * load / loadSum)));
                        }
                    }
                    while (packetsByDestination.get(k).size() > 0) {
                        for (int i = 0; i < routingTable.get(k).size(); i++) {
                            int idx = indexByRouter(routingTable.get(k).get(i));
                            for (int j = 0; j < roundRobin.get(i); j++) {
                                OutQueues.get(idx).putPacket(packetsByDestination.get(k).get(0));
                                packetsByDestination.get(k).remove(0);
                                if (packetsByDestination.get(k).size() == 0) {
                                    break;
                                }
                            }
                            if (packetsByDestination.get(k).size() == 0) {
                                break;
                            }
                        }
                    }
                }
            }
            case LVP -> {
                ArrayList<ArrayList<Integer>> taskDistribution = new ArrayList<>();
                for (int k = 0; k < routingTable.size(); k++) {
                    ArrayList<Integer> distribution = new ArrayList<>(Collections.nCopies(routingTable.get(k).size(), 0));
                    distribution.set(0, packetsByDestination.get(k).size());
                    taskDistribution.add(distribution);
                }
                int iteration = 0;
                while (iteration < maxIterationsLVP) {
                    iteration++;
                    boolean isBalanced = true;
                    for (int k = 0; k < packetsByDestination.size(); k++) {
                        int destination = packetsByDestination.get(k).get(0).Receiver;
                        ArrayList<Integer> freeThroughput = new ArrayList<>();
                        for (int i = 0; i < routingTable.get(k).size(); i++) {
                            int realLoad = getLink(Neighbours.get(i)).getEnqueuedTasks();
                            int availableThroughput = AvailableThroughput.get(destination).get(i);
                            freeThroughput.add(availableThroughput - (realLoad + taskDistribution.get(k).get(i)));
                        }
                        ArrayList<Integer> control = new ArrayList<>();
                        for (int i = 0; i < freeThroughput.size(); i++) {
                            int sum = 0;
                            for (Integer integer : freeThroughput) {
                                sum += (freeThroughput.get(i) - integer);
                            }
                            int controlValue = (int) (Math.round(sum * gammaLVP));
                            if (controlValue < 0) {
                                if (taskDistribution.get(k).get(i) != 0) {
                                    isBalanced = false;
                                }
                            }
                            control.add(controlValue);
                        }
                        for (int i = 0; i < control.size(); i++) {
                            if ((control.get(i) >= 0) || (taskDistribution.get(k).get(i) == 0)) {
                                continue;
                            }
                            int minControl = Math.min(-control.get(i), taskDistribution.get(k).get(i));
                            for (int j = 0; j < control.size(); j++) {
                                if (control.get(j) <= 0) {
                                    continue;
                                }
                                int change = Math.min(minControl, control.get(j));
                                taskDistribution.get(k).set(i, taskDistribution.get(k).get(i) - change);
                                taskDistribution.get(k).set(j, taskDistribution.get(k).get(j) + change);
                                control.set(i, control.get(i) + change);
                                control.set(j, control.get(j) - change);
                                minControl = Math.min(-control.get(i), taskDistribution.get(k).get(i));
                            }
                        }
                    }
                    if (isBalanced) {
                        break;
                    }
                }
                ArrayList<Integer> count = new ArrayList<>(Collections.nCopies(OutQueues.size(), 0));
                for (int k = 0; k < routingTable.size(); k++) {
                    for (int i = 0; i < routingTable.get(k).size(); i++) {
                        int idx = indexByRouter(routingTable.get(k).get(i));
                        for (int j = 0; j < taskDistribution.get(k).get(i); j++) {
                            if (packetsByDestination.get(k).size() == 0) {
                                continue;
                            }
                            OutQueues.get(idx).putPacket(packetsByDestination.get(k).get(0));
                            packetsByDestination.get(k).remove(0);
                            count.set(idx, count.get(idx) + 1);
                        }
                    }
                }
            }
        }
    }

    void massPacketRoute(ArrayList<NetworkPacket> packets) {
        ArrayList<ArrayList<NetworkPacket>> packetsByDestination = new ArrayList<>();
        ArrayList<ArrayList<Integer>> routingTable = new ArrayList<>();
        for (int i = 0; i < RoutingTable.size(); i++) {
            packetsByDestination.add(new ArrayList<>());
        }
        for (NetworkPacket packet : packets) {
            packetsByDestination.get(packet.Receiver).add(packet);
        }
        for (int i = packetsByDestination.size() - 1; i >= 0; i--) {
            if (packetsByDestination.get(i).size() == 0) {
                packetsByDestination.remove(i);
                continue;
            }
            ArrayList<Integer> possibleRoutes = RoutingTable.get(packetsByDestination.get(i).get(0).Receiver);
            if (possibleRoutes.size() == 1) {
                int idx = indexByRouter(possibleRoutes.get(0));
                for (NetworkPacket packet : packetsByDestination.get(i)) {
                    OutQueues.get(idx).putPacket(packet);
                }
                packetsByDestination.remove(i);
            } else {
                routingTable.add(possibleRoutes);
            }
        }
        if (packetsByDestination.size() == 0)  {
            return;
        }
        Collections.reverse(routingTable);
        massPacketRouting(packetsByDestination, routingTable);
    }

    void routePackets() {
        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < Neighbours.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order);
        ArrayList<NetworkPacket> packetsToRoute = new ArrayList<>();
        boolean continueRouting;
        do {
            continueRouting = false;
            for (int i : order) {
                for (int j = 0; j < Synchronizer.Priorities.size(); j++) {
                    NetworkPacket packet = InQueues.get(i).getPacket(j);
                    while (packet != null) {
                        packetsToRoute.add(packet);
                        packet = InQueues.get(i).getPacket(j);
                    }
                }
                if (!InQueues.get(i).isEmpty()) {
                    continueRouting = true;
                }
            }
        } while (continueRouting);
        packetsToRoute.addAll(InnerQueue);
        InnerQueue.clear();
        if ((Synchronizer.Algorithm == Synchronizer.AlgorithmType.CLOVE) || (Synchronizer.Algorithm == Synchronizer.AlgorithmType.LVP)) {
            massPacketRoute(packetsToRoute);
        }
        else {
            for (NetworkPacket packet : packetsToRoute) {
                singlePacketRoute(packet);
            }
        }
    }

    NetworkLink getLink(int router) {
        int self = Integer.parseInt(getLocalName());
        for (NetworkLink link : Synchronizer.Links) {
            if (((link.Nodes.get(0) == self) && (link.Nodes.get(1) == router)) ||
                    ((link.Nodes.get(1) == self) && (link.Nodes.get(0) == router))) {
                return link;
            }
        }
        return null;
    }

    void sendPackets() {
        ArrayList<Double> weights = new ArrayList<>();
        for (int priority : Synchronizer.Priorities) {
            weights.add(priority * 1.0 / Synchronizer.PrioritySum);
        }
        for (int i = 0; i < OutQueues.size(); i++) {
            if (OutQueues.get(i).getPacketsNumber() == 0) {
                continue;
            }
            int router = Neighbours.get(i);
            NetworkLink link = getLink(router);
            ArrayList<Integer> taskPattern = new ArrayList<>();
            for (int p = 0; p < weights.size(); p++) {
                int num = Math.max((int)Math.round(link.Throughput * weights.get(p) / 2), 1);
                for (int n = 0; n < num; n++) {
                    taskPattern.add(p);
                }
            }
            int priorityIdx = 0;
            while (OutQueues.get(i).getPacketsNumber() > 0) {
                if (link.canSend(Integer.parseInt(getLocalName()))) {
                    NetworkPacket packet = OutQueues.get(i).getPacket(taskPattern.get(priorityIdx));
                    priorityIdx = (priorityIdx + 1) % taskPattern.size();
                    if (packet == null) {
                        packet = OutQueues.get(i).getPacket();
                    }
                    ACLMessage message = new ACLMessage(Synchronizer.TRANSMIT_TASK);
                    AID receiver = new AID(Integer.toString(router), AID.ISLOCALNAME);
                    message.addReceiver(receiver);
                    message.setContent(packet.asString());
                    send(message);
                } else {
                    break;
                }
            }
        }
    }

    double getLoad(int idx) {
        NetworkLink link = getLink(Neighbours.get(idx));
        return (1.0 * link.getEnqueuedTasks()) / link.Throughput;
    }

    void auxiliaryPreJob() {
        if (Synchronizer.Algorithm == Synchronizer.AlgorithmType.LVP) {
            ArrayList<Integer> availableThroughput = new ArrayList<>();
            for (int i = 0; i < RoutingTable.size(); i++) {
                int sum = 0;
                for (int j = 0; j < RoutingTable.get(i).size(); j++) {
                    int router = RoutingTable.get(i).get(j);
                    int throughput = getLink(router).Throughput - OutQueues.get(indexByRouter(router)).getPacketsNumber();
                    sum += Math.min(throughput, AvailableThroughput.get(i).get(j));
                }
                availableThroughput.add(sum);
            }
            for (Integer neighbour : Neighbours) {
                getLink(neighbour).setDownstreamRouting(getLocalName(), availableThroughput);
            }
        }
    }

    void auxiliaryAfterJob() {
        if (Synchronizer.Algorithm == Synchronizer.AlgorithmType.LVP) {
            for (int i = 0; i < RoutingTable.size(); i++) {
                for (int j = 0; j < RoutingTable.get(i).size(); j++) {
                    NetworkLink link = getLink(RoutingTable.get(i).get(j));
                    ArrayList<Integer> throughput = link.getDownstreamRouting(getLocalName());
                    int availableThroughput;
                    if (throughput.size() > 0) {
                        availableThroughput = Math.min(link.Throughput, throughput.get(i));
                    } else {
                        availableThroughput = link.Throughput;
                    }
                    AvailableThroughput.get(i).set(j, availableThroughput);
                }
            }
        }
        for (int i = 0; i < Neighbours.size(); i++) {
            getLink(Neighbours.get(i)).setEnqueuedTasks(getLocalName(), OutQueues.get(i).getPacketsNumber());
        }
    }

    void nextFrame() {
        auxiliaryPreJob();
        sendPackets();
        routePackets();
        auxiliaryAfterJob();
        finishFrame();
    }

    void receivePacket(NetworkPacket packet, int sender) {
        if (packet.Receiver == Integer.parseInt(getLocalName())) {
            packet.finishTask(Synchronizer.Frame);
        }
        else {
            int idx = indexByRouter(sender);
            InQueues.get(idx).putPacket(packet);
        }
    }

    void receiveMessage(ACLMessage message) {
        switch (message.getPerformative()) {
            case Synchronizer.TRANSMIT_TASK -> {
                NetworkPacket receivedPacket = new NetworkPacket(message.getContent());
                receivePacket(receivedPacket, Integer.parseInt(message.getSender().getLocalName()));
            }
            case Synchronizer.FINISH_WORK -> {
                ACLMessage reply = message.createReply();
                reply.setPerformative(Synchronizer.FINISH_WORK);
                send(reply);
                this.doDelete();
            }
        }
    }
}
