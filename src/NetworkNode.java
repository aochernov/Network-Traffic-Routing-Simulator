import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NetworkNode extends Agent {
    static Object Lock = new Object();
    ArrayList<Integer> Neighbours;
    ArrayList<ArrayList<Integer>> Routers;
    ArrayList<ArrayList<NetworkTask>> Tasks;

    @Override
    protected  void setup() {
        Object args[] = getArguments();
        Tasks = new ArrayList<>();
        for (int i = 0; i < Synchronizer.Priorities.size(); i++) {
            Tasks.add(new ArrayList<>());
        }
        Neighbours = (ArrayList<Integer>) args[0];
        Routers = (ArrayList<ArrayList<Integer>>)args[1];
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

    void getNewTasks(ArrayList<NetworkTask> tasks) {
        for (NetworkTask task : tasks) {
            Tasks.get(task.Priority - 1).add(task);
        }
    }

    int chooseRouter(int to) {
        return Routers.get(to).get(0);
    }

    ArrayList<ArrayList<ArrayList<NetworkTask>>> prepareTransmission() {
        ArrayList<ArrayList<ArrayList<NetworkTask>>> tasksByRouter = new ArrayList<>();
        for (int i = 0; i < Neighbours.size(); i++) {
            ArrayList<ArrayList<NetworkTask>> List = new ArrayList<>();
            for (int j = 0; j < Tasks.size(); j++) {
                List.add(new ArrayList<>());
            }
            tasksByRouter.add(List);
        }
        for (int p = 0; p < Tasks.size(); p++) {
            for (int t = 0; t < Tasks.get(p).size(); t++) {
                NetworkTask task = Tasks.get(p).get(t);
                if (task.Receiver == Integer.parseInt(getLocalName())) {
                    task.finishTask(Synchronizer.Frame);
                    Tasks.get(p).remove(task);
                    t--;
                    continue;
                }
                int router = chooseRouter(task.Receiver);
                int idx = 1;
                for (int j = 0; j < Neighbours.size(); j++) {
                    if (router == Neighbours.get(j)) {
                        idx = j;
                        break;
                    }
                }
                tasksByRouter.get(idx).get(task.Priority - 1).add(task);
            }
        }
        return  tasksByRouter;
    }

    boolean listAnd(ArrayList<Boolean> list) {
        for (boolean l : list) {
            if (!l) {
                return false;
            }
        }
        return true;
    }

    void transmitTasks(ArrayList<ArrayList<ArrayList<NetworkTask>>> tasksByRouter) {
        for (int r = 0; r < tasksByRouter.size(); r++) {
            int router = Neighbours.get(r);
            NetworkLink link = getLink(router);
            ArrayList<ArrayList<NetworkTask>> tasks = tasksByRouter.get(r);
            ArrayList<Boolean> isTransmitted = new ArrayList<>(Collections.nCopies(tasks.size(), false));
            while(!listAnd(isTransmitted)) {
                for (int p = 0; p < tasks.size(); p++) {
                    if (isTransmitted.get(p)) {
                        continue;
                    }
                    int numOfTasks = Synchronizer.Priorities.get(p);
                    for (int t = 0; t < tasks.get(p).size(); t++) {
                        NetworkTask task = tasks.get(p).get(t);
                        tasks.get(p).remove(task);
                        if (link.canSend()) {
                            ACLMessage message = new ACLMessage(Synchronizer.TRANSMIT_TASK);
                            AID receiver = new AID(Integer.toString(router), AID.ISLOCALNAME);
                            message.addReceiver(receiver);
                            message.setContent(task.asString());
                            send(message);
                            Tasks.get(p).remove(task);
                            t--;
                            numOfTasks--;
                            if (numOfTasks == 0) {
                                break;
                            }
                        } else {
                            isTransmitted.set(p, true);
                        }
                    }
                    if (tasks.get(p).size() == 0) {
                        isTransmitted.set(p, true);
                    }
                }
            }
        }
    }

    void nextFrame() {
        schedule();
        transmitTasks(prepareTransmission());
        finishFrame();
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

    void receiveMessage(ACLMessage message) {
        switch (message.getPerformative()) {
            case Synchronizer.TRANSMIT_TASK: {
                NetworkTask receivedTask = new NetworkTask(message.getContent());
                Tasks.get(receivedTask.Priority - 1).add(receivedTask);
                break;
            }
            case Synchronizer.FINISH_WORK: {
                ACLMessage reply = message.createReply();
                reply.setPerformative(Synchronizer.FINISH_WORK);
                send(reply);
                this.doDelete();
                break;
            }
        }
    }

    void schedule() {
        return;
    }
}
