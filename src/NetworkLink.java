import java.util.ArrayList;

public class NetworkLink {
    ArrayList<Integer> Nodes;
    int Throughput;
    int PlannedThroughput;
    int AvailableThroughput;
    Object Lock = new Object();

    NetworkLink(NetworkController.LinkInfo link) {
        Nodes = link.Nodes;
        Throughput = link.Throughput;
        AvailableThroughput = Throughput;
        PlannedThroughput = 0;
    }

    void nextFrame() {
        AvailableThroughput = Throughput;
        PlannedThroughput = 0;
    }

    int getRemainingThroughput() {
        int rem;
        synchronized (Lock) {
            rem = Throughput - PlannedThroughput;
        }
        return rem;
    }

    void planThroughput(int amount) {
        synchronized (Lock) {
            PlannedThroughput += amount;
        }
    }

    boolean canSend() {
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
