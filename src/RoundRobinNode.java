import java.util.ArrayList;
import java.util.Collections;

public class RoundRobinNode  extends NetworkNode{
    ArrayList<Integer> Iterators;

    @Override
    protected  void setup() {
        super.setup();
        Iterators = new ArrayList<>(Collections.nCopies(Routers.size(), 0));
    }

    @Override
    int chooseRouter(int to) {
        int iterator = Iterators.get(to);
        int router = Routers.get(to).get(iterator);
        Iterators.set(to, (iterator + 1) % Routers.get(to).size());
        return router;
    }
}