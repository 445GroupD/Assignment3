import java.util.Map;

/**
 * Created by Lincoln W Daniel on 4/22/2016.
 *
 * A wrapper for outgoing packets sent by a leader to followers.
 * This object is local to the leader's server and
 * handles committing the actual outgoing AppPacket's data to the leaders persistent database
 * when the packet has received enough acks from followers to commit.
 *
 * This ensures we are only commiting a packet when we have majority acks,
 * we only commit a packet once,
 * and we create the logIndex in correct sequence to be sent to the followers upon committing to persistent db.
 *
 */
public class LeaderPacket
{
    //used to provide sequence number to all outgoing packets
    private static int lastSequenceNumber = 0;

    //the number of acks this packet has received to confirm commiting to db
    int acksReceived = 0;
    //the actual packet being sent to followers
    AppPacket packet;
    /*the sequence number of the packet.
    Important Note*: In order to make sure a picture is committed before a comment on that picture,
    it is intuitive that we block when sending a picture and wait to commit it
    before allowing the user to send any data related to that picture.
    */
    private final int sequenceNumber;
    /*The term number by which the leader is in when this outgoing packet is sent*/
    private final long term;
    //whether or not the packet has been committed to the leader's persistent db
    private boolean alreadyCommittedToDB = false;

    /**
     *
     * @param packet the actual packet being sent to followers
     */
    LeaderPacket(AppPacket packet)
    {
        this.packet = packet;
        this.sequenceNumber = packet.getSequenceNumber();
        this.term = packet.getTerm();
    }

    /**
     * Increases the number of acks this outgoing packet has received to save the
     * @param majority
     * @param db
     * @return
     */
    public int confirm(int majority, Map<Integer, String> db)
    {
        //increment the number of acks this packet has received
        acksReceived++;
        if (!alreadyCommittedToDB)
        {
            boolean receivedMajority = acksReceived >= majority;
            if (receivedMajority)
            {
                int logIndex = db.size() + 1;
                db.put(logIndex, packet.getReadableData());

                //set already committed so this packet is not committed on the next ack
                alreadyCommittedToDB = true;

                //return the logIndex as true to the caller so it can tell the others to commit, too
                return logIndex;
            }
        }

        //nothing interesting happened.
        //Return -1 as false so the caller doesn't tell the others to commit.
        return -1;
    }



    public int getSequenceNumber()
    {
        return sequenceNumber;
    }

    public long getTerm()
    {
        return term;
    }
    public static int getNextSequenceNumber()
    {
        //return the next sequence number to be set on a packet being sent to followers
        return ++lastSequenceNumber;
    }
}
