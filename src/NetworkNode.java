import java.util.*;

public class NetworkNode {
    class RoutingInfo {
        int Sender;
        int Receiver;
        int WaitingTasks;
        int RoutedTasks;
        ArrayList<Integer> Routers;
        ArrayList<Integer> Routed;
        ArrayList<Integer> Sent;

        RoutingInfo(int sender, int receiver) {
            Sender = sender;
            Receiver = receiver;
            WaitingTasks = 0;
            RoutedTasks = 0;
            Routers = new ArrayList<>();
            Routed = new ArrayList<>();
            Sent = new ArrayList<>();
        }
    }

    static float Gamma = 0.6f;
    int Name;
    ArrayList<ArrayList<Integer>> Routing;
    Map<Integer, NetworkLink> Links;
    ArrayList<NetworkConnection> ReceivingConnections;
    ArrayList<NetworkTask> WaitingTasks;
    ArrayList<RoutingInfo> RoutingInfos;

    NetworkNode(NetworkController.NodeInfo node) {
        Name = node.Name;
        Routing = node.Routing;
        Links = new HashMap<>();
        ReceivingConnections = new ArrayList<>();
        WaitingTasks = new ArrayList<>();
        RoutingInfos = new ArrayList<>();
    }

    void BindLink(NetworkLink link, int destination) {
        Links.put(destination, link);
    }

    void AddReceivingConnection(NetworkConnection con) {
        ReceivingConnections.add(con);
    }

    int GetRoutingInfo(int from, int to) {
        int i = 0;
        for (RoutingInfo info : RoutingInfos) {
            if ((info.Sender == from) && (info.Receiver == to)) {
                return i;
            }
            i++;
        }
        RoutingInfos.add(new RoutingInfo(from, to));
        return i;
    }

    void CheckRouting() {
        for (NetworkTask task : WaitingTasks) {
            RoutingInfos.get(GetRoutingInfo(task.Sender, task.Receiver)).WaitingTasks++;
        }
        boolean needRerouting = false;
        for (RoutingInfo info : RoutingInfos) {
            if ((info.WaitingTasks != info.RoutedTasks)) {
                needRerouting = true;
                break;
            }
        }
        if (needRerouting) {
            Route();
        }
    }

    int CalculateRemainingThroughput(int destination) {
        if (destination == Name) {
            return 0;
        }
        int value = 0;
        for (int node : Routing.get(destination)) {
            value += Links.get(node).GetRemainingThroughput();
            value += Math.min(Links.get(node).GetRemainingThroughput(), (Links.get(node).GetNode(Name)).CalculateRemainingThroughput(destination));
        }
        return value;
    }

    void Route() {
        for (RoutingInfo info : RoutingInfos) {
            info.RoutedTasks = info.WaitingTasks;
            info.Routers.clear();
            info.Sent.clear();
            info.Routed.clear();
        }
        /*//First chosen path
        for (RoutingInfo info : RoutingInfos) {
            int router = Routing.get(info.Receiver).get(0);
            info.Routers.add(router);
            info.Routed.add(info.WaitingTasks);
            info.Sent.add(0);
        }*/
        /*//Random path
        for (RoutingInfo info : RoutingInfos) {
            for (Integer r : Routing.get(info.Receiver)) {
                info.Routers.add(r);
                info.Sent.add(0);
                info.Routed.add(0);
            }
            Random rand = new Random();
            int num = info.Routed.size();
            for (int i = 0; i < info.WaitingTasks; i++) {
                int chosen = rand.nextInt(num);
                info.Routed.set(chosen, info.Routed.get(chosen) + 1);
            }
        }*/
        /*//Round-Robin
        for (RoutingInfo info : RoutingInfos) {
            for (Integer r : Routing.get(info.Receiver)) {
                info.Routers.add(r);
                info.Sent.add(0);
                info.Routed.add(0);
            }
            int num = info.Routed.size();
            for (int i = 0; i < info.WaitingTasks; i++) {
                int chosen = i % num;
                info.Routed.set(chosen, info.Routed.get(chosen) + 1);
            }
        }*/
        //Local Voting
        for (NetworkLink link : Links.values()) {
            link.UnUseThroughput(Name);
        }
        for (RoutingInfo info : RoutingInfos) {
            for (Integer r : Routing.get(info.Receiver)) {
                info.Routers.add(r);
                info.Sent.add(0);
                info.Routed.add(0);
            }
            info.Routed.set(0, info.WaitingTasks);
            Links.get(info.Routers.get(0)).AddUsedThroughput(Name, info.WaitingTasks);
            boolean continueVoting = true;
            int step = 0;
            while (continueVoting && (step < 25)) {
                Map<Integer, Integer> remThr = new HashMap<>();
                int sum = 0;
                for (Integer node : info.Routers) {
                    int value = Math.min(Links.get(node).GetRemainingThroughput(), Links.get(node).GetNode(Name).CalculateRemainingThroughput(info.Receiver));
                    sum += value;
                    remThr.put(node, value);
                }
                Map<Integer, Integer> control = new HashMap<>();
                for (Integer node : remThr.keySet()) {
                    int controlValue = Math.round(Gamma * (remThr.size() * remThr.get(node) - sum));
                    control.put(node, controlValue);
                }
                continueVoting = false;
                for (Integer node : control.keySet()) {
                    int value = control.get(node);
                    if (value < 0) {
                        for (Integer node_ : control.keySet()) {
                            int value_ = control.get(node_);
                            if (value_ > 0) {
                                int index = info.Routers.indexOf(node);
                                int index_ = info.Routers.indexOf(node_);
                                int change = Math.min(value_, -value);
                                if (change > 0) {
                                    continueVoting = true;
                                    info.Routed.set(index, info.Routed.get(index) - change);
                                    info.Routed.set(index_, info.Routed.get(index_) + change);
                                    value += change;
                                    Links.get(node).AddUsedThroughput(Name, -change);
                                    Links.get(node_).AddUsedThroughput(Name, change);
                                }
                                if (value == 0) {
                                    break;
                                }
                            }
                        }
                    }
                }
                step++;
            }
        }
    }

    void SendTasks() {
        CheckRouting();
        for (NetworkTask task : WaitingTasks) {
            RoutingInfo info = RoutingInfos.get(GetRoutingInfo(task.Sender, task.Receiver));
            int j = 0;
            while (true) {
                if (info.Sent.get(j) < info.Routed.get(j)) {
                    info.Sent.set(j, info.Sent.get(j) + 1);
                    int router = info.Routers.get(j);
                    Links.get(router).AddToQueue(router, task);
                    break;
                }
                j++;
            }
        }
        for (RoutingInfo info : RoutingInfos) {
            info.WaitingTasks = 0;
            for (int i = 0; i < info.Sent.size(); i++) {
                info.Sent.set(i, 0);
            }
        }
        WaitingTasks.clear();
    }

    void ReceiveTasks(ArrayList<NetworkTask> tasks) {
        for (NetworkTask task : tasks) {
            int receiver = task.Receiver;
            if (receiver == Name) {
                for (NetworkConnection con : ReceivingConnections) {
                    if (con.ID == task.ID) {
                        con.ReceivedTasks.add(task);
                    }
                }
            }
            else {
                WaitingTasks.add(task);
            }
        }
    }
}
