import java.util.Random;

public class RandomNode extends NetworkNode{
    @Override
    int chooseRouter(int to) {
        Random random = new Random();
        int num = random.nextInt(Routers.get(to).size());
        return Routers.get(to).get(num);
    }
}
