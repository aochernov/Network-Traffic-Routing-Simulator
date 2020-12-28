public class Main {
    static NetworkController Controller;

    public static void main(String[] args) {
        Controller = new NetworkController(NetworkController.LOCAL_VOTING, "C:\\Users\\Drew\\Desktop\\Network Load Balancing\\test.json");
        Controller.run();
    }
}
