import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class Synchronizer extends Agent {
    enum AlgorithmType {ECMP, RPS, DRILL, CLOVE, DeTail, LVP}

    static ArrayList<NetworkConnection> Connections = new ArrayList<>();
    static ArrayList<NetworkLink> Links = new ArrayList<>();
    static ArrayList<Integer> Priorities = new ArrayList<>();
    static int PrioritySum;
    static int Frame;
    static AlgorithmType Algorithm;
    HashSet<Integer> SyncAgents = new HashSet<>();
    int NumOfAgents;

    static final int AGENT_TO_SYNC = 0;
    static final int SYNC_TO_AGENT = 1;
    static final int TRANSMIT_TASK = 2;
    static final int FINISH_WORK = 3;

    @Override
    protected  void setup() {
        Object[] args = getArguments();
        NumOfAgents = (Integer)args[0];
        addBehaviour(new SynchronizerBehaviour(this, TimeUnit.MILLISECONDS.toMillis(10)));
    }

    void addSyncAgent(int id) {
        SyncAgents.add(id);
        if (SyncAgents.size() != NumOfAgents) {
            return;
        }
        SyncAgents.clear();
        Frame++;
        System.out.println("Frame: " + Frame);
        for (NetworkConnection connection : Connections) {
            connection.nextFrame();
        }
        for (NetworkLink link : Links) {
            link.nextFrame();
        }
        ACLMessage message = new ACLMessage(Synchronizer.SYNC_TO_AGENT);
        for (int i = 0; i < NumOfAgents; i++) {
            AID receiver = new AID(Integer.toString(i), AID.ISLOCALNAME);
            message.addReceiver(receiver);
        }
        send(message);
    }

    void destruct() {
        SyncAgents.clear();
        ACLMessage message = new ACLMessage(Synchronizer.FINISH_WORK);
        for (int i = 0; i < NumOfAgents; i++) {
            AID receiver = new AID(Integer.toString(i), AID.ISLOCALNAME);
            message.addReceiver(receiver);
        }
        send(message);
        Main.Controller.finish(Connections);
        doDelete();
    }
}
