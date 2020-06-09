import java.util.ArrayList;

public class NetworkConnection {
    static int ConnectionID = 0;
    int ID;
    int Sender;
    int Receiver;
    int Tasks;
    int TaskArrivalRate;
    int TaskArrivalSize;
    int StartFrame;
    int FinishFrame;
    int Priority;
    NetworkNode SenderNode;
    ArrayList<NetworkTask> ReceivedTasks;
    int ExpectedTasks;

    NetworkConnection(NetworkController.ConnectionInfo con, ArrayList<NetworkNode> nodes) {
        Priority = con.Priority;
        ID = ConnectionID;
        ConnectionID++;
        Sender = con.Sender;
        Receiver = con.Receiver;
        Tasks = con.Tasks;
        ExpectedTasks = Tasks;
        FinishFrame = -1;
        ReceivedTasks = new ArrayList<>();
        TaskArrivalRate = con.TaskArrivalRate;
        TaskArrivalSize = con.TaskArrivalSize;
        StartFrame = con.StartFrame;
        for (NetworkNode node : nodes) {
            if (Sender == node.Name) {
                SenderNode = node;
            }
            else if (Receiver == node.Name) {
                node.AddReceivingConnection(this);
            }
        }
    }

    void GenerateTasks(int frame) {
        if ((frame < StartFrame) || (((frame - StartFrame) % TaskArrivalRate) != 0) || (Tasks == 0)) {
            return;
        }
        ArrayList<NetworkTask> tasks = new ArrayList<>();
        int taskNum = Math.min(Tasks, TaskArrivalSize);
        for (int i = 0; i < taskNum; i++) {
            tasks.add(new NetworkTask(Sender, Receiver, frame, ID, Priority));
        }
        Tasks -= taskNum;
        SenderNode.ReceiveTasks(tasks);
    }

    boolean CheckConnectionEnd(int frame) {
        for (NetworkTask task : ReceivedTasks) {
            if (task.FinishFrame == -1) {
                task.FinishFrame = frame;
            }
        }
        return (ExpectedTasks == ReceivedTasks.size());
    }
}