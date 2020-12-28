import java.util.ArrayList;
import java.util.Collections;

public class LocalVotingNode extends NetworkNode {
    ArrayList<Integer> PlannedTasks;
    ArrayList<ArrayList<Integer>> RoutingPlans;
    ArrayList<ArrayList<Integer>> Schedule;
    double Alpha = 0.2;
    int MaxIteration = 20;

    @Override
    protected  void setup() {
        super.setup();
        Object args[] = getArguments();
        PlannedTasks = new ArrayList<>(Collections.nCopies(Routers.size(), 0));
        RoutingPlans = new ArrayList<>();
        for (int i = 0; i < Routers.size(); i++) {
            RoutingPlans.add(new ArrayList<>(Collections.nCopies(Routers.get(i).size(), 0)));
        }
    }

    void planThroughput(ArrayList<Integer> difference) {
        for (int i = 0; i < difference.size(); i++) {
            int dif = difference.get(i);
            if (dif == 0) {
                continue;
            }
            ArrayList<Integer> possibleRouters = Routers.get(i);
            if (dif > 0) {
                int routersNum = possibleRouters.size();
                int fraction = Math.floorDiv(dif, routersNum);
                int rem = dif - routersNum * fraction;
                for (int j = 0; j < possibleRouters.size(); j++) {
                    int idx = -1;
                    for (int k = 0; k < Neighbours.size(); k++) {
                        if (possibleRouters.get(j) == Neighbours.get(k)) {
                            idx = k;
                            break;
                        }
                    }
                    int change = fraction;
                    if (j == 0) {
                        change += rem;
                    }
                    RoutingPlans.get(i).set(j, RoutingPlans.get(i).get(j) + change);
                }
            } else {
                dif = (-dif);
                for (int j = 0; j < possibleRouters.size(); j++) {
                    int idx = -1;
                    for (int k = 0; k < Neighbours.size(); k++) {
                        if (possibleRouters.get(j) == Neighbours.get(k)) {
                            idx = k;
                            break;
                        }
                    }
                    int change = Math.min(RoutingPlans.get(i).get(j), dif);
                    RoutingPlans.get(i).set(j, RoutingPlans.get(i).get(j) - change);
                    dif -= change;
                    if (dif == 0) {
                        break;
                    }
                }
            }
            PlannedTasks.set(i, PlannedTasks.get(i) + difference.get(i));
        }
    }

    void localVoting() {
        int iter = 0;
        ArrayList<Boolean> voted = new ArrayList<>(Collections.nCopies(PlannedTasks.size(), false));
        while (!listAnd(voted) && (iter < MaxIteration)) {
            for (int i = 0; i < PlannedTasks.size(); i++) {
                if (voted.get(i)) {
                    continue;
                }
                int taskNum = PlannedTasks.get(i);
                if (taskNum == 0) {
                    voted.set(i, true);
                    continue;
                }
                if (RoutingPlans.get(i).size() == 1) {
                    NetworkLink link = getLink(Routers.get(i).get(0));
                    link.planThroughput(RoutingPlans.get(i).get(0));
                    voted.set(i, true);
                    continue;
                }
                ArrayList<Integer> remainingThroughput = new ArrayList<>();
                for (int j = 0; j < Routers.get(i).size(); j++) {
                    NetworkLink link = getLink(Routers.get(i).get(j));
                    remainingThroughput.add(link.getRemainingThroughput() - RoutingPlans.get(i).get(j));
                }
                boolean votingFinished = true;
                for (int x = 0; x < remainingThroughput.size(); x++) {
                    int value = 0;
                    for (int y = 0; y < remainingThroughput.size(); y++) {
                        if (x == y) {
                            value += remainingThroughput.get(x);
                        } else {
                            value += Math.round(Alpha * (remainingThroughput.get(y) - remainingThroughput.get(x)));
                        }
                    }
                    int dif = remainingThroughput.get(x) - value;
                    if (dif != 0) {
                        RoutingPlans.get(i).set(x, RoutingPlans.get(i).get(x) + dif);
                        NetworkLink link = getLink(Routers.get(i).get(x));
                        link.planThroughput(dif);
                        votingFinished = false;
                    }
                }
                voted.set(i, votingFinished);
            }
            iter++;
        }
    }

    @Override
    void schedule() {
        ArrayList<Integer> newPlannedTasks = new ArrayList<>(Collections.nCopies(PlannedTasks.size(), 0));
        for (ArrayList<NetworkTask> tasks : Tasks) {
            for (NetworkTask task : tasks) {
                newPlannedTasks.set(task.Receiver, newPlannedTasks.get(task.Receiver) + 1);
            }
        }
        ArrayList<Integer> difference = new ArrayList<>();
        boolean isSame = true;
        for (int i = 0; i < PlannedTasks.size(); i++) {
            if (Integer.parseInt(getLocalName()) == i) {
                difference.add(0);
                continue;
            }
            int dif = newPlannedTasks.get(i) - PlannedTasks.get(i);
            difference.add(dif);
            if (dif != 0) {
                isSame = false;
            }
        }
        if (isSame) {
            for (int i = 0; i < RoutingPlans.size(); i++) {
                for (int j = 0; j < RoutingPlans.get(i).size(); j++) {
                    NetworkLink link = getLink(Routers.get(i).get(j));
                    link.planThroughput(RoutingPlans.get(i).get(j));
                }
            }
        } else {
            planThroughput(difference);
        }
        localVoting();
        prepareSchedule();
    }

    void prepareSchedule() {
        Schedule = new ArrayList<>();
        for (int i = 0; i < RoutingPlans.size(); i++) {
            ArrayList<Integer> list = new ArrayList<>();
            for (int j = 0; j < RoutingPlans.get(i).size(); j++) {
                list.add(RoutingPlans.get(i).get(j));
            }
            Schedule.add(list);
        }
    }

    @Override
    int chooseRouter(int to) {
        for (int i = 0; i < Schedule.get(to).size(); i++) {
            if (Schedule.get(to).get(i) > 0) {
                Schedule.get(to).set(i, Schedule.get(to).get(i) - 1);
                return Routers.get(to).get(i);
            }
        }
        return Routers.get(to).get(0);
    }
}
