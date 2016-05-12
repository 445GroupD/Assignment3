package server.Packet;

import org.apache.commons.lang3.ArrayUtils;
import utils.AppUtils;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import static java.lang.String.format;

/**
 *
 */
public class AppPacket
{
    public static final int PACKET_SIZE = 10000;
    private final int headerSize;
    private final int serverId;
    private final PacketType type;
    private final int leaderId;
    private final long term;
    private final long highest;
    private final byte[] data;

    private final int sequenceNumber;

    private final int logIndex;


    private final int dataType;

    //Receiver

    /**
     * Constructor for an AppPacket from a byte array. This is used when receiving a packet to convert it into an more
     * easily managed object.
     *
     * @param data byte array of a packet that contains all the data.
     */
    public AppPacket(byte[] data)
    {
        this.serverId = AppUtils.bytesToInt(ArrayUtils.subarray(data, 0, 4));
        this.type = PacketType.fromInt(AppUtils.bytesToInt(ArrayUtils.subarray(data, 4, 8)));
        this.leaderId = AppUtils.bytesToInt(ArrayUtils.subarray(data, 8, 12));
        this.term = AppUtils.bytesToLong(ArrayUtils.subarray(data, 12, 20));
        this.highest = AppUtils.bytesToLong(ArrayUtils.subarray(data, 20, 28));
        this.sequenceNumber = AppUtils.bytesToInt(ArrayUtils.subarray(data, 28, 32));
        this.logIndex = AppUtils.bytesToInt(ArrayUtils.subarray(data, 32, 36));
        this.dataType = AppUtils.bytesToInt(ArrayUtils.subarray(data, 36, 40));
        this.data = ArrayUtils.subarray(data, 40, data.length);
        headerSize = 0;
    }

    /**
     * Constructor for AppPacket that takes all the parameters needed to create an AppPacket
     *
     * @param serverId       serverId of the server that is creating this packet
     * @param type           what type of packet is being sent.
     * @param leaderId       the currently believed leader
     * @param term           the current term
     * @param member         how many members are in the group
     * @param sequenceNumber the current sequence number
     * @param logIndex       the logIndex of this log
     * @param dataType       the type of log that was sent
     * @param data           the actual data
     */
    public AppPacket(int serverId, PacketType type, int leaderId, long term, long member, int sequenceNumber, int logIndex, int dataType, String data)
    {
        this.serverId = serverId;
        this.type = type;
        this.leaderId = leaderId;
        this.term = term;
        this.highest = member;
        this.logIndex = logIndex;
        this.sequenceNumber = sequenceNumber;
        this.dataType = dataType;

        headerSize = Integer.BYTES + Integer.BYTES + Integer.BYTES + Long.BYTES + Long.BYTES;
        this.data = fill(data);
    }

    /**
     * fills the body field if to fill the packet, these will be trimmed when received.
     *
     * @param data the data that needs to be appended to to fill the packet
     * @return the new filled byte array.
     */
    private byte[] fill(String data)
    {
        int fill = PACKET_SIZE - headerSize - data.length();
        if (type.equals(PacketType.PICTURE))
        {
            System.out.println("Picture Data FILL size = " + fill);
            ;
            System.out.println("Picture Data Length = " + data.length());
        }
        for (int i = 0; i < fill; i++)
        {
            data += " ";
        }
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

    /**
     * method to get a datagram packet from an AppPacket
     *
     * @param address address you want to send to.
     * @param port    port you want to send to.
     * @return the DatagramPacket that was made from this AppPacket
     */
    public DatagramPacket getDatagram(InetAddress address, int port)
    {

        byte[] datagramArray = ArrayUtils.addAll(AppUtils.intToBytes(serverId), AppUtils.intToBytes(type.getIdent()));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(leaderId));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.longToBytes(term));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.longToBytes(highest));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(sequenceNumber));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(logIndex));
        datagramArray = ArrayUtils.addAll(datagramArray, AppUtils.intToBytes(dataType));
        datagramArray = ArrayUtils.addAll(datagramArray, data);
        return new DatagramPacket(datagramArray, datagramArray.length, address, port);
    }

    /**
     * gets the body of the AppPacket
     *
     * @return body of the AppPacket
     */
    public byte[] getData()
    {
        return data;
    }

    /**
     * gets the server id of the AppPacket
     *
     * @return gets the server id of the AppPacket
     */
    public int getServerId()
    {
        return serverId;
    }

    /**
     * gets the Type of packet of the AppPacket
     *
     * @return Type of packet of the AppPacket
     */
    public PacketType getType()
    {
        return type;
    }

    /**
     * gets the log type of the AppPacket
     *
     * @return log type of the AppPacket
     */
    public int getDataType()
    {
        return dataType;
    }

    /**
     * gets the LeaderId of the AppPacket
     *
     * @return LeaderId of the AppPacket
     */
    public long getLeaderId()
    {
        return leaderId;
    }

    /**
     * gets the term of the AppPacket
     *
     * @return term of the AppPacket
     */
    public long getTerm()
    {
        return term;
    }

    public long getHighest()
    {
        return highest;
    }

    public int getSequenceNumber()
    {
        return sequenceNumber;
    }

    public int getLogIndex()
    {
        return logIndex;
    }

    /**
     * Returns a trimmed string representation of the data in this packet.
     *
     * @return
     */
    public String getReadableData()
    {
        return new String(getData()).trim();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return format("Packet { From: %s | Term: %s | Sequence No: %s | Data: %s }", serverId, term, sequenceNumber, getReadableData());
    }

    /**
     * Enum for what type of data the packet contains
     */
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
        HEARTBEAT_ACK(7)
                {
                    @Override
                    public void parseData(byte[] data)
                    {

                    }
                },
        VOTE_REQUEST(8)
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

        public static PacketType fromString(String ident)
        {
            for (PacketType current : PacketType.values())
            {
                if (ident.equals(current.toString()))
                {
                    return current;
                }
            }
            return null;
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
