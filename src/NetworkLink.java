import java.util.ArrayList;

public class NetworkLink {
    ArrayList<Integer> Nodes;
    ArrayList<Boolean> SendPermitted;
    ArrayList<Integer> EnqueuedTasks;
    ArrayList<ArrayList<Integer>> DownstreamRouting;
    int Throughput;
    int AvailableThroughput;
    final Object Lock = new Object();

    NetworkLink(NetworkController.LinkInfo link) {
        Nodes = link.Nodes;
        Throughput = link.Throughput;
        AvailableThroughput = Throughput;
        SendPermitted = new ArrayList<>();
        EnqueuedTasks = new ArrayList<>();
        DownstreamRouting = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            SendPermitted.add(true);
            EnqueuedTasks.add(0);
            DownstreamRouting.add(new ArrayList<>());
        }
    }

    int getEnqueuedTasks() {
        return (EnqueuedTasks.get(0) + EnqueuedTasks.get(1));
    }

    void setEnqueuedTasks(String sender, int queue) {
        EnqueuedTasks.set((Integer.parseInt(sender) == Nodes.get(0)) ? 0 : 1, queue);
    }

    void setDownstreamRouting(String sender, ArrayList<Integer> routing) {
        synchronized (Lock) {
            if (Integer.parseInt(sender) == Nodes.get(0)) {
                DownstreamRouting.set(0, routing);
            } else {
                DownstreamRouting.set(1, routing);
            }
        }
    }

    ArrayList<Integer> getDownstreamRouting(String sender) {
        ArrayList<Integer> result;
        synchronized (Lock) {
            result = (Integer.parseInt(sender) == Nodes.get(0)) ? DownstreamRouting.get(1) : DownstreamRouting.get(0);
        }
        return result;
    }

    void nextFrame() {
        AvailableThroughput = Throughput;
    }

    boolean canSend(int sender) {
        boolean sendPermitted = (sender == Nodes.get(0)) ? SendPermitted.get(0) : SendPermitted.get(1);
        if (!sendPermitted) {
            return false;
        }
        synchronized (Lock) {
            if (AvailableThroughput > 0) {
                AvailableThroughput--;
                return true;
            } else {
                return false;
            }
        }
    }
}
