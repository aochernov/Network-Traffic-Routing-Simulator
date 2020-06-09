public class NetworkTask {
    int Sender;
    int Receiver;
    int GenerationFrame;
    int FinishFrame;
    int ID;
    int Priority;

    NetworkTask(int sender, int receiver, int generationFrame, int id, int priority) {
        Sender = sender;
        Receiver = receiver;
        GenerationFrame = generationFrame;
        FinishFrame = -1;
        ID = id;
        Priority = priority;
    }
}
