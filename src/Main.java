public class Main {
    public static void main(String[] args) {
        for (int i = 1; i < 10; i++) {
            NetworkNode.Gamma = i * 1.0f / 10;
            NetworkController Controller = new NetworkController("C:\\Users\\Andrew\\Desktop\\Time Slot Distribution System\\test.json");
            Controller.initialize();
            Controller.run();
            Controller.getResults(1);
            Controller.getResults(2);
        }
    }
}
