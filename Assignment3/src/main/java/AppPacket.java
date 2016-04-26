import org.apache.commons.lang3.ArrayUtils;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class AppPacket
{
    private static final int PACKET_SIZE = 1500;
    private final int headerSize;
    private final int serverId;
    private final PacketType type;
    private final int leaderId;
    private final long term;
    private final long member;
    private final byte[] data;

    private final int seq;

    private final int logIndex;

    //Receiver
    public AppPacket(byte[] data)
    {
        this.serverId = AppUtils.bytesToInt(ArrayUtils.subarray(data, 0, 4));
        this.type = PacketType.fromInt(AppUtils.bytesToInt(ArrayUtils.subarray(data, 4, 8)));
        this.leaderId = AppUtils.bytesToInt(ArrayUtils.subarray(data, 8, 12));
        this.term = AppUtils.bytesToLong(ArrayUtils.subarray(data, 12, 20));
        this.member = AppUtils.bytesToLong(ArrayUtils.subarray(data, 20, 28));
        this.seq = AppUtils.bytesToInt(ArrayUtils.subarray(data, 28, 32));
        this.logIndex = AppUtils.bytesToInt(ArrayUtils.subarray(data, 32, 36));
        this.data = ArrayUtils.subarray(data, 36, data.length);
        headerSize = 0;
    }

    //Sender
    public AppPacket(int serverId, PacketType type, int leaderId, long term, long member, int seq, int logIndex, String data)
    {
        this.serverId = serverId;
        this.type = type;
        this.leaderId = leaderId;
        this.term = term;
        this.member = member;
        this.logIndex = logIndex;
        this.seq = seq;

        headerSize = Integer.BYTES + Integer.BYTES + Integer.BYTES + Long.BYTES + Long.BYTES;
        this.data = fill(data);
    }

    private byte[] fill(String data)
    {
        System.out.println(data);
        int fill = PACKET_SIZE - headerSize - data.length();
        for (int i = 0; i < fill; i++)
        {
            data += " ";
        }
        System.out.println(data);
        try
        {
            return data.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public DatagramPacket getDatagram(InetAddress address, int port)
    {

        byte[] datagramArray = ArrayUtils.addAll(AppUtils.intToBytes(serverId), AppUtils.intToBytes(type.getIdent()));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(leaderId));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.longToBytes(term));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.longToBytes(member));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(seq));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(logIndex));
        datagramArray = ArrayUtils.addAll(datagramArray, data);
        return new DatagramPacket(datagramArray, datagramArray.length, address, port);
    }

    public byte[] getData()
    {
        return data;
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

    public int getSeq()
    {
        return seq;
    }

    public int getLogIndex()
    {
        return logIndex;
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
                        new String(data);
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
                },
        ACK(5)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        COMMIT(6)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        VOTE_REQUEST(7)
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
