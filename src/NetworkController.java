import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class NetworkController {
    NetworkInfo Settings;
    ArrayList<NetworkNode> Nodes;
    ArrayList<NetworkLink> Links;
    ArrayList<NetworkConnection> Connections, FinishedConnections;
    int LastStart;

    NetworkController(String filename) {
        LastStart = 0;
        try {
            Settings = (new Gson()).fromJson(new BufferedReader(new FileReader(filename)), NetworkInfo.class);
        } catch (FileNotFoundException e) {}
        for (ConnectionInfo info : Settings.Connections) {
            LastStart = Math.max(info.StartFrame, LastStart);
        }
    }

    public class ConnectionInfo {
        int Sender;
        int Receiver;
        int Tasks;
        int TaskArrivalRate;
        int TaskArrivalSize;
        int StartFrame;
        int Priority;
    }

    public class LinkInfo {
        ArrayList<Integer> Nodes;
        int Throughput;
    }

    public class NodeInfo {
        int Name;
        ArrayList<ArrayList<Integer>> Routing;
    }

    public class NetworkInfo {
        ArrayList<LinkInfo> Links;
        ArrayList<ConnectionInfo> Connections;
        ArrayList<NodeInfo> Nodes;

        ArrayList<Object[]> getArgs() {
            ArrayList<Object[]> args = new ArrayList<>();
            for (NodeInfo node : Nodes) {
                Object nodeArgs[] = {node.Name, node.Routing};
                args.add(nodeArgs);
            }
            return args;
        }
    }

    void initialize() {
        Nodes = new ArrayList<>();
        for (NodeInfo info : Settings.Nodes) {
            Nodes.add(new NetworkNode(info));
        }
        Links = new ArrayList<>();
        for (LinkInfo info : Settings.Links) {
            Links.add(new NetworkLink(info, Nodes));
        }
        Connections = new ArrayList<>();
        for (ConnectionInfo info : Settings.Connections) {
            Connections.add(new NetworkConnection(info, Nodes));
        }
        FinishedConnections = new ArrayList<>();
    }

    void run() {
        int frame = 0;
        while ((Connections.size() != 0) || (frame <= LastStart)) {
            for (NetworkConnection con : Connections) {
                con.GenerateTasks(frame);
            }
            for (NetworkNode node : Nodes) {
                node.SendTasks();
            }
            for (NetworkLink link : Links) {
                link.Transmit();
            }
            frame++;
            //System.out.println("Frame №" + frame);
            for (NetworkConnection con : Connections) {
                if (con.CheckConnectionEnd(frame)) {
                    con.FinishFrame = frame;
                    FinishedConnections.add(con);
                }
            }
            for (NetworkConnection con : FinishedConnections) {
                Connections.remove(con);
            }
            /*
            if ((frame > 0) && ((frame % 20) == 0)) {
                getIntermediateResults(frame - 20);
                NetworkNode.Gamma += 0.1f;
                if (NetworkNode.Gamma >= 1.0f) {
                    NetworkNode.Gamma = 0.1f;
                }
            }
            */
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
        }
    }

    void getIntermediateResults(int begin) {
        float delayMin = Float.MAX_VALUE, delayMax = Float.MIN_VALUE, delayAvg = 0.0f, delayFairness = 0.0f;
        int num = 0;
        for (NetworkConnection connection : FinishedConnections) {
            float delay = 0.0f;
            for (NetworkTask task : connection.ReceivedTasks) {
                float taskDelay = task.FinishFrame - begin;
                delay += taskDelay;
            }
            delay /= connection.ReceivedTasks.size();
            delayAvg += delay;
            delayMin = Math.min(delay, delayMin);
            delayMax = Math.max(delay, delayMax);
            delayFairness += (delay * delay);
            num++;
        }
        FinishedConnections.clear();
        for (NetworkConnection connection : Connections) {
            float delay = 0.0f;
            for (NetworkTask task : connection.ReceivedTasks) {
                float taskDelay = task.FinishFrame - begin;
                delay += taskDelay;
            }
            if (delay > 0) {
                delay /= connection.ReceivedTasks.size();
                delayAvg += delay;
                delayMin = Math.min(delay, delayMin);
                delayMax = Math.max(delay, delayMax);
                delayFairness += (delay * delay);
                num++;
                connection.ExpectedTasks -= connection.ReceivedTasks.size();
                connection.ReceivedTasks.clear();
            }
        }
        delayAvg /= num;
        delayFairness = (delayAvg * delayAvg) / (delayFairness / num);
        System.out.println("Gamma " + NetworkNode.Gamma);
        System.out.println("Average: " + delayAvg);
        System.out.println("Maximal: " + delayMax);
        System.out.println("Minimal: " + delayMin);
        System.out.println("Fairness: " + delayFairness);
    }

    void getResults(int prio) {
        float deliveryMin = Float.MAX_VALUE, deliveryMax = Float.MIN_VALUE, deliveryAvg = 0.0f, deliveryFairness = 0.0f;
        float delayMin = Float.MAX_VALUE, delayMax = Float.MIN_VALUE, delayAvg = 0.0f, delayFairness = 0.0f;
        float throughputMin = Float.MAX_VALUE, throughputMax = Float.MIN_VALUE, throughputAvg = 0.0f, throughputFairness = 0.0f;
        int num = 0;
        for (NetworkConnection connection : FinishedConnections) {
            if (connection.Priority == prio) {
                num++;
                //Delivery time
                float delivery = connection.FinishFrame - connection.StartFrame;
                deliveryAvg += delivery;
                deliveryMin = Math.min(delivery, deliveryMin);
                deliveryMax = Math.max(delivery, deliveryMax);
                deliveryFairness += (delivery * delivery);
                //Delay
                float delay = 0.0f;
                for (NetworkTask task : connection.ReceivedTasks) {
                    float taskDelay = task.FinishFrame - task.GenerationFrame;
                    delay += taskDelay;
                }
                delay /= connection.ReceivedTasks.size();
                delayAvg += delay;
                delayMin = Math.min(delay, delayMin);
                delayMax = Math.max(delay, delayMax);
                delayFairness += (delay * delay);
                //Throughput
                float throughput = connection.ReceivedTasks.size() / delivery;
                throughputAvg += throughput;
                throughputMin = Math.min(throughput, throughputMin);
                throughputMax = Math.max(throughput, throughputMax);
                throughputFairness += (throughput * throughput);
            }
        }
        deliveryAvg /= num;
        delayAvg /= num;
        throughputAvg /= num;
        deliveryFairness = (deliveryAvg * deliveryAvg) / (deliveryFairness / num);
        delayFairness = (delayAvg * delayAvg) / (delayFairness / num);
        throughputFairness = (throughputAvg * throughputAvg) / (throughputFairness / num);

        //Print
        System.out.println("Priority " + prio);
        System.out.println("Gamma " + NetworkNode.Gamma);
        System.out.println("###");
        System.out.println("Delivery Time:");
        System.out.println("Average: " + deliveryAvg);
        System.out.println("Maximal: " + deliveryMax);
        System.out.println("Minimal: " + deliveryMin);
        System.out.println("Fairness: " + deliveryFairness);
        System.out.println("---");
        System.out.println("Delay:");
        System.out.println("Average: " + delayAvg);
        System.out.println("Maximal: " + delayMax);
        System.out.println("Minimal: " + delayMin);
        System.out.println("Fairness: " + delayFairness);
        System.out.println("---");
        System.out.println("Throughput:");
        System.out.println("Average: " + throughputAvg);
        System.out.println("Maximal: " + throughputMax);
        System.out.println("Minimal: " + throughputMin);
        System.out.println("Fairness: " + throughputFairness);
        System.out.println("###");
        System.out.println();
    }
}
