import com.google.gson.Gson;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;

public class NetworkController {
    private static NetworkInfo Settings;
    private ArrayList<NetworkConnection> FinishedConnections;
    private boolean IsFinished;

    NetworkController(String filename) {
        FinishedConnections = new ArrayList<>();
        IsFinished = false;
        try {
            Settings = (new Gson()).fromJson(new BufferedReader(new FileReader(filename)), NetworkInfo.class);
            Settings.findNeighbours();
            Settings.calculatePaths();
        } catch (FileNotFoundException ignored) {}
    }

    public static class ConnectionInfo {
        int Sender;
        int Receiver;
        int Tasks;
        int TaskArrivalRate;
        int TaskArrivalSize;
        int StartFrame;
        int Priority;
    }

    public static class LinkInfo {
        ArrayList<Integer> Nodes;
        int Throughput;
    }

    public static class NetworkInfo {
        String Algorithm;
        ArrayList<LinkInfo> Links;
        ArrayList<ConnectionInfo> Connections;
        int Nodes;
        ArrayList<Integer> Priorities;
        ArrayList<ArrayList<Integer>> Neighbours;
        ArrayList<ArrayList<ArrayList<Integer>>> Routers;

        void findNeighbours() {
            Neighbours = new ArrayList<>();
            for (int i = 0 ; i < Nodes; i++) {
                Neighbours.add(new ArrayList<>());
            }
            for (LinkInfo link : Links) {
                int begin = link.Nodes.get(0);
                int end = link.Nodes.get(1);
                Neighbours.get(begin).add(end);
                Neighbours.get(end).add(begin);
            }
        }

        void calculatePaths() {
            ArrayList<ArrayList<Integer>> distances = new ArrayList<>();
            for (int i = 0; i < Nodes; i++) {
                distances.add(new ArrayList<>(Collections.nCopies(Nodes, Nodes)));
                distances.get(i).set(i, 0);
            }
            for (LinkInfo link : Links) {
                int begin = link.Nodes.get(0);
                int end = link.Nodes.get(1);
                distances.get(begin).set(end, 1);
                distances.get(end).set(begin, 1);
            }
            for (int k = 0; k < Nodes; k++) {
                for (int i = 0; i < Nodes; i++) {
                    for (int j = 0; j < Nodes; j++) {
                        int newDistance = distances.get(i).get(k) + distances.get(k).get(j);
                        if (distances.get(i).get(j) > newDistance) {
                            distances.get(i).set(j, newDistance);
                        }
                    }
                }
            }
            Routers = new ArrayList<>();
            for (int i = 0; i < Nodes; i++) {
                Routers.add(new ArrayList<>());
                for (int j = 0; j < Nodes; j++) {
                    Routers.get(i).add(new ArrayList<>());
                    if (i != j) {
                        int dist = distances.get(i).get(j);
                        for (Integer neighbour : Neighbours.get(i)) {
                            if (distances.get(neighbour).get(j) == (dist - 1)) {
                                Routers.get(i).get(j).add(neighbour);
                            }
                        }
                    }
                }
            }
        }
    }

    void run() {
        NetworkConnection.nextID = 0;
        NetworkConnection.IsFinished = false;
        switch (Settings.Algorithm) {
            case "RPS" -> Synchronizer.Algorithm = Synchronizer.AlgorithmType.RPS;
            case "DRILL" -> Synchronizer.Algorithm = Synchronizer.AlgorithmType.DRILL;
            case "CLOVE" -> Synchronizer.Algorithm = Synchronizer.AlgorithmType.CLOVE;
            case "DETAIL", "DeTail" -> Synchronizer.Algorithm = Synchronizer.AlgorithmType.DeTail;
            case "LVP" -> Synchronizer.Algorithm = Synchronizer.AlgorithmType.LVP;
            default -> Synchronizer.Algorithm = Synchronizer.AlgorithmType.ECMP;
        }
        Synchronizer.Frame = 0;
        Synchronizer.PrioritySum = 0;
        Synchronizer.Priorities.clear();
        for (int priority : Settings.Priorities) {
            Synchronizer.Priorities.add(priority);
            Synchronizer.PrioritySum += priority;
        }
        Synchronizer.Connections.clear();
        for (ConnectionInfo connection : Settings.Connections) {
            Synchronizer.Connections.add(new NetworkConnection(connection));
        }
        Synchronizer.Links.clear();
        for (LinkInfo link : Settings.Links) {
            Synchronizer.Links.add(new NetworkLink(link));
        }
        jade.core.Runtime runtime = jade.core.Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.MAIN_PORT, "10098");
        profile.setParameter(Profile.GUI, "false");
        ContainerController container = runtime.createMainContainer(profile);
        try {
            Object[] args = {Settings.Nodes};
            AgentController sync = container.createNewAgent("sync", "Synchronizer", args);
            sync.start();
            for (int i = 0; i < Settings.Nodes; i++) {
                Object[] arguments = {Settings.Neighbours.get(i), Settings.Routers.get(i)};
                AgentController agent = container.createNewAgent(Integer.toString(i), "NetworkNode", arguments);
                agent.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (!IsFinished) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            container.kill();
        } catch (Exception e) {
            e.printStackTrace();
        }
        runtime.setCloseVM(true);
        getResults();
    }

    void finish(ArrayList<NetworkConnection> connections) {
        FinishedConnections = connections;
        IsFinished = true;
    }

    void getResults() {
        System.out.println();
        System.out.println("***");
        switch (Synchronizer.Algorithm) {
            case ECMP -> System.out.println("ECMP");
            case RPS -> System.out.println("RPS");
            case DRILL -> System.out.println("DRILL");
            case CLOVE -> System.out.println("CLOVE");
            case DeTail -> System.out.println("DeTail");
            case LVP -> System.out.println("LVP");
        }
        System.out.println("***");
        for (int p = 1; p <= Settings.Priorities.size(); p++) {
            float deliveryMin = Float.MAX_VALUE, deliveryMax = Float.MIN_VALUE, deliveryAvg = 0.0f, deliveryFairness = 0.0f;
            float delayMin = Float.MAX_VALUE, delayMax = Float.MIN_VALUE, delayAvg = 0.0f, delayFairness = 0.0f;
            float throughputMin = Float.MAX_VALUE, throughputMax = Float.MIN_VALUE, throughputAvg = 0.0f, throughputFairness = 0.0f;
            int num = 0;
            for (NetworkConnection connection : FinishedConnections) {
                if (connection.Priority != p) {
                    continue;
                }
                num++;
                //Delivery time
                float delivery = connection.FinishFrame - connection.StartFrame;
                deliveryAvg += delivery;
                deliveryMin = Math.min(delivery, deliveryMin);
                deliveryMax = Math.max(delivery, deliveryMax);
                deliveryFairness += (delivery * delivery);
                //Delay
                float delay = 0.0f;
                for (NetworkPacket task : connection.FinishedTasks) {
                    float taskDelay = task.FinishFrame - task.GenerationFrame;
                    delay += taskDelay;
                }
                delay /= connection.FinishedTasks.size();
                delayAvg += delay;
                delayMin = Math.min(delay, delayMin);
                delayMax = Math.max(delay, delayMax);
                delayFairness += (delay * delay);
                //Throughput
                float throughput = connection.FinishedTasks.size() / delivery;
                throughputAvg += throughput;
                throughputMin = Math.min(throughput, throughputMin);
                throughputMax = Math.max(throughput, throughputMax);
                throughputFairness += (throughput * throughput);
                //System.out.println(connection.Sender + " --> " + connection.Receiver + "; Priority = " + connection.Priority);
                //System.out.println("Delivery: " + delivery + ", Delay: " + delay + ", Throughput: " + throughput);
            }
            deliveryAvg /= num;
            delayAvg /= num;
            throughputAvg /= num;
            deliveryFairness = (deliveryAvg * deliveryAvg) / (deliveryFairness / num);
            delayFairness = (delayAvg * delayAvg) / (delayFairness / num);
            throughputFairness = (throughputAvg * throughputAvg) / (throughputFairness / num);

            //Print
            System.out.println("Priority " + p);
            System.out.println("###");
            System.out.println("DeliveryTime:");
            System.out.println(deliveryAvg);
            System.out.println(deliveryMax);
            System.out.println(deliveryMin);
            System.out.println(deliveryFairness);
            /*System.out.println("Average: " + deliveryAvg);
            System.out.println("Maximal: " + deliveryMax);
            System.out.println("Minimal: " + deliveryMin);
            System.out.println("Fairness: " + deliveryFairness);*/
            System.out.println("---");
            System.out.println("Delay:");
            System.out.println(delayAvg);
            System.out.println(delayMax);
            System.out.println(delayMin);
            System.out.println(delayFairness);
            /*System.out.println("Average: " + delayAvg);
            System.out.println("Maximal: " + delayMax);
            System.out.println("Minimal: " + delayMin);
            System.out.println("Fairness: " + delayFairness);*/
            System.out.println("---");
            System.out.println("Throughput:");
            System.out.println(throughputAvg);
            System.out.println(throughputMax);
            System.out.println(throughputMin);
            System.out.println(throughputFairness);
            /*System.out.println("Average: " + throughputAvg);
            System.out.println("Maximal: " + throughputMax);
            System.out.println("Minimal: " + throughputMin);
            System.out.println("Fairness: " + throughputFairness);*/
            System.out.println("###");
            System.out.println();
        }
    }
}
