import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

public class SynchronizerBehaviour extends TickerBehaviour {
    Synchronizer SyncronizerNode;

    SynchronizerBehaviour(Synchronizer agent, long period) {
        super(agent, period);
        this.setFixedPeriod(true);
        SyncronizerNode = agent;
    }

    @Override
    protected  void onTick() {
        if (NetworkConnection.IsFinished) {
            SyncronizerNode.destruct();
            return;
        }
        ACLMessage message = getAgent().receive();
        if (message == null) {
            return;
        }
        if (message.getPerformative() == Synchronizer.AGENT_TO_SYNC) {
            SyncronizerNode.addSyncAgent(Integer.parseInt(message.getSender().getLocalName()));
        }
    }
}
