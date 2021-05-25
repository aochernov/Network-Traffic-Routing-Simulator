public class NetworkPacket {
    int Receiver;
    int GenerationFrame;
    int Connection;
    int Priority;
    int FinishFrame;

    NetworkPacket(int receiver, int generationFrame, int connection, int priority) {
        Receiver = receiver;
        GenerationFrame = generationFrame;
        Connection = connection;
        Priority = priority;
    }

    NetworkPacket(String task) {
        String[] parts = task.split("_");
        Priority = Integer.parseInt(parts[0]);
        Connection = Integer.parseInt(parts[1]);
        Receiver = Integer.parseInt(parts[2]);
        GenerationFrame = Integer.parseInt(parts[3]);
    }

    String asString() {
        return Priority + "_" + Connection + "_" + Receiver + "_" + GenerationFrame;
    }

    void finishTask(int frame) {
        FinishFrame = frame;
        for (NetworkConnection connection : Synchronizer.Connections) {
            if (connection.ID == Connection) {
                connection.finishTask(this);
                return;
            }
        }
    }
}
