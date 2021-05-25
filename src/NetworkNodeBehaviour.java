import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

public class NetworkNodeBehaviour extends TickerBehaviour {
    NetworkNode Node;

    NetworkNodeBehaviour(NetworkNode agent, long period) {
        super(agent, period);
        this.setFixedPeriod(true);
        Node = agent;
    }

    @Override
    protected  void onTick() {
        ACLMessage message = getAgent().receive();
        if (message != null) {
            if (message.getPerformative() == Synchronizer.SYNC_TO_AGENT) {
                Node.nextFrame();
            } else {
                Node.receiveMessage(message);
            }
        }
    }
}
