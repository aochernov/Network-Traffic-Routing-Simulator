import java.util.ArrayList;

public class NetworkLink {
    ArrayList<NetworkNode> Nodes;
    ArrayList<ArrayList<NetworkTask>> Queues;
    int Throughput;
    ArrayList<Integer> UsedThroughputs;

    NetworkLink(NetworkController.LinkInfo link, ArrayList<NetworkNode> nodes) {
        Nodes = new ArrayList<>();
        Throughput = link.Throughput;
        for (NetworkNode node : nodes) {
            int name = node.Name;
            if (name == link.Nodes.get(0)) {
                node.BindLink(this, link.Nodes.get(1));
                Nodes.add(node);
            }
            else if (name == link.Nodes.get(1)) {
                node.BindLink(this, link.Nodes.get(0));
                Nodes.add(node);
            }
        }
        Queues = new ArrayList<>();
        Queues.add(new ArrayList<>());
        Queues.add(new ArrayList<>());
        UsedThroughputs = new ArrayList<>();
        UsedThroughputs.add(0);
        UsedThroughputs.add(0);
    }

    void UnUseThroughput(int name) {
        if (name == Nodes.get(0).Name) {
            UsedThroughputs.set(0, 0);
        }
        else {
            UsedThroughputs.set(1, 1);
        }
    }

    void AddUsedThroughput(int name, int value) {
        if (name == Nodes.get(0).Name) {
            UsedThroughputs.set(0, UsedThroughputs.get(0) + value);
        }
        else {
            UsedThroughputs.set(1, UsedThroughputs.get(1) + value);
        }
    }

    int GetRemainingThroughput() {
        return (Throughput - (UsedThroughputs.get(0) + UsedThroughputs.get(1)));
    }

    NetworkNode GetNode(int node) {
        if (Nodes.get(0).Name == node) {
            return Nodes.get(1);
        }
        else {
            return Nodes.get(0);
        }
    }

    void AddToQueue(int to, NetworkTask task) {
        if (to == Nodes.get(0).Name) {
            Queues.get(0).add(task);
        }
        else {
            Queues.get(1).add(task);
        }
    }

    void Transmit() {
        int fstThroughput = Math.min(Queues.get(0).size(), Throughput);
        int sndThroughput = Math.min(Queues.get(1).size(), Throughput - fstThroughput);
        ArrayList<NetworkTask> tasks = new ArrayList<>();
        for (int i = 0; i < fstThroughput; i++) {
            tasks.add(Queues.get(0).get(0));
            Queues.get(0).remove(0);
        }
        if (tasks.size() > 0) {
            //System.out.println("Transmitting " + tasks.size() + " tasks from " + Nodes.get(1).Name + " to " + Nodes.get(0).Name);
            Nodes.get(0).ReceiveTasks(tasks);
        }
        tasks.clear();
        for (int i = 0; i < sndThroughput; i++) {
            tasks.add(Queues.get(1).get(0));
            Queues.get(1).remove(0);
        }
        if (tasks.size() > 0) {
            //System.out.println("Transmitting " + tasks.size() + " tasks from " + Nodes.get(0).Name + " to " + Nodes.get(1).Name);
            Nodes.get(1).ReceiveTasks(tasks);
        }
    }
}
