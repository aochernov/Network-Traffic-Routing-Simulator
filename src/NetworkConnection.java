import java.util.ArrayList;

public class NetworkConnection {
    static int nextID;
    static boolean IsFinished;
    int ID;
    int Sender;
    int Receiver;
    int Tasks;
    int RemainingTasks;
    int TaskArrivalRate;
    int TaskArrivalSize;
    int StartFrame;
    int FinishFrame;
    int Priority;
    ArrayList<NetworkTask> FinishedTasks;
    NetworkNode SenderNode;
    NetworkNode ReceiverNode;
    Object Lock = new Object();

    NetworkConnection(NetworkController.ConnectionInfo con) {
        synchronized (Lock) {
            ID = nextID;
            nextID++;
        }
        Priority = con.Priority;
        Sender = con.Sender;
        Receiver = con.Receiver;
        Tasks = con.Tasks;
        RemainingTasks = Tasks;
        FinishFrame = -1;
        FinishedTasks = new ArrayList<>();
        TaskArrivalRate = con.TaskArrivalRate;
        TaskArrivalSize = con.TaskArrivalSize;
        StartFrame = con.StartFrame;
    }

    void register(NetworkNode node, boolean isSender) {
        if (isSender) {
            SenderNode = node;
            generateTasks();
        }
        else {
            ReceiverNode = node;
        }
    }

    void nextFrame() {
        generateTasks();
    }

    void generateTasks() {
        if ((RemainingTasks > 0) && (Synchronizer.Frame >= StartFrame) && ((Synchronizer.Frame - StartFrame) % TaskArrivalRate == 0)) {
            ArrayList<NetworkTask> newTasks = new ArrayList<>();
            int numOfTasks = Math.min(RemainingTasks, TaskArrivalSize);
            for (int i = 0; i < numOfTasks; i++) {
                newTasks.add(new NetworkTask(Receiver, Synchronizer.Frame, ID, Priority));
            }
            SenderNode.getNewTasks(newTasks);
            RemainingTasks -= numOfTasks;
        }
    }

    void finishTask(NetworkTask task) {
        FinishedTasks.add(task);
        if (FinishedTasks.size() == Tasks) {
            FinishFrame = Synchronizer.Frame;
            synchronized (Lock) {
                nextID--;
                if (nextID == 0) {
                    IsFinished = true;
                }
            }
        }
    }
}