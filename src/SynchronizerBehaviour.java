import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

public class SynchronizerBehaviour extends TickerBehaviour {
    Synchronizer SynchronizerNode;

    SynchronizerBehaviour(Synchronizer agent, long period) {
        super(agent, period);
        this.setFixedPeriod(true);
        SynchronizerNode = agent;
    }

    @Override
    protected  void onTick() {
        if (NetworkConnection.IsFinished) {
            SynchronizerNode.destruct();
            return;
        }
        ACLMessage message = getAgent().receive();
        if (message == null) {
            return;
        }
        if (message.getPerformative() == Synchronizer.AGENT_TO_SYNC) {
            SynchronizerNode.addSyncAgent(Integer.parseInt(message.getSender().getLocalName()));
        }
    }
}
