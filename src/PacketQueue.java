import java.util.ArrayList;
import java.util.Random;

public class PacketQueue {
    ArrayList<ArrayList<NetworkPacket>> Packets;

    PacketQueue() {
        Packets = new ArrayList<>();
        for (int i = 0; i < Synchronizer.Priorities.size(); i++) {
            Packets.add(new ArrayList<>());
        }
    }

    void putPacket(NetworkPacket packet) {
        int p = packet.Priority - 1;
        Packets.get(p).add(packet);
    }

    boolean isEmpty() {
        for (ArrayList<NetworkPacket> packets : Packets) {
            if (packets.size() > 0) {
                return false;
            }
        }
        return true;
    }

    NetworkPacket getPacket(int priority) {
        Random r = new Random();
        ArrayList<NetworkPacket> packets = Packets.get(priority);
        if (packets.size() > 0) {
            int num = r.nextInt(packets.size());
            NetworkPacket packet = packets.get(num);
            Packets.get(priority).remove(num);
            return packet;
        }
        else {
            return null;
        }
    }

    int getPacketsNumber() {
        int num = 0;
        for (ArrayList<NetworkPacket> packets : Packets) {
            num += packets.size();
        }
        return num;
    }

    NetworkPacket getPacket() {
        Random r = new Random();
        for (ArrayList<NetworkPacket> packets : Packets) {
            if (packets.size() > 0) {
                int num = r.nextInt(packets.size());
                NetworkPacket packet = packets.get(num);
                packets.remove(num);
                return packet;
            }
        }
        return null;
    }
}
