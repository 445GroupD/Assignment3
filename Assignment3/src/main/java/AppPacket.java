import org.apache.commons.lang3.ArrayUtils;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class AppPacket
{
    private final int serverId;
    private final PacketType type;
    private final int leaderId;
    private final long term;
    private final long member;
    private final byte[] data;

    public AppPacket(byte[] data)
    {
        this.serverId = AppUtils.bytesToInt(ArrayUtils.subarray(data, 0, 3));
        this.type = PacketType.fromInt(AppUtils.bytesToInt(ArrayUtils.subarray(data, 4, 7)));
        this.leaderId = AppUtils.bytesToInt(ArrayUtils.subarray(data, 8, 11));
        this.term = AppUtils.bytesToLong(ArrayUtils.subarray(data, 12, 19));
        this.member = AppUtils.bytesToLong(ArrayUtils.subarray(data, 20, 27));
        this.data = ArrayUtils.subarray(data,28,data.length -1);
    }

    public AppPacket(int serverId, PacketType type, int leaderId, long term, long member, byte[] data)
    {
        this.serverId = serverId;
        this.type = type;
        this.leaderId = leaderId;
        this.term = term;
        this.member = member;
        this.data = data;
    }

    public DatagramPacket getDatagram(InetAddress address, int port)
    {

        byte[] datagramArray = ArrayUtils.addAll(AppUtils.intToBytes(serverId), AppUtils.intToBytes(type.getIdent()));
        ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(leaderId));
        ArrayUtils.addAll(datagramArray, AppUtils.longToBytes(term));
        ArrayUtils.addAll(datagramArray, AppUtils.longToBytes(member));
        ArrayUtils.addAll(datagramArray, data);
        return new DatagramPacket(datagramArray,datagramArray.length,address,port);
    }

    public void getData()
    {
        type.parseData(data);
    }

    public int getServerId()
    {
        return serverId;
    }

    public PacketType getType()
    {
        return type;
    }

    public long getLeaderId()
    {
        return leaderId;
    }

    public long getTerm()
    {
        return term;
    }

    public long getMember()
    {
        return member;
    }

    public enum PacketType
    {
        PICTURE(0)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        COMMENT(1)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        GPS(2)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        VOTE(3)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        HEARTBEAT(4)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                };


        private final int ident;

        public static PacketType fromInt(int ident)
        {
            PacketType returnType = null;
            for (PacketType current : PacketType.values())
            {
                if (current.getIdent() == ident)
                {
                    returnType = current;
                    break;
                }
            }
            if (returnType == null)
            {
                throw new AssertionError("incorrect type Identifier received : " + ident);
            }
            return returnType;
        }

        public int getIdent()
        {
            return ident;
        }

        PacketType(int ident)
        {
            this.ident = ident;
        }


        public abstract void parseData(byte[] data);
    }

}
