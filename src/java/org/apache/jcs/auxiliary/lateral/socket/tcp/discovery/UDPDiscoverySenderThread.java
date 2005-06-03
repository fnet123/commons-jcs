package org.apache.jcs.auxiliary.lateral.socket.tcp.discovery;

import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Used to periodically broadcast our location to other caches that might be
 * listening.
 * 
 * @author Aaron Smuts
 *  
 */
public class UDPDiscoverySenderThread
    implements Runnable
{

    private final static Log log = LogFactory.getLog( UDPDiscoverySenderThread.class );

    // the UDP multicast port
    private String discoveryAddress = "";

    private int discoveryPort = 0;

    // the host and port we listen on for TCP socket connections
    private String myHostName = null;

    private int myPort = 0;

    private ArrayList cacheNames = new ArrayList();

    /**
     * @param cacheNames
     *            The cacheNames to set.
     */
    protected void setCacheNames( ArrayList cacheNames )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "Resetting cacheNames = [" + cacheNames + "]" );
        }
        this.cacheNames = cacheNames;
    }

    /**
     * @return Returns the cacheNames.
     */
    protected ArrayList getCacheNames()
    {
        return cacheNames;
    }

    /**
     * Constructs the sender with the port to tell others to conenct to.
     * <p>
     * On construction the sender will request that the other caches let it know
     * their addresses.
     * 
     * @param discoveryAddress
     *            host to broadcast to
     * @param discoveryPort
     *            port to broadcast to
     * @param myHostName
     *            host name we can be found at
     * @param myPort
     *            port we are listening on
     */
    public UDPDiscoverySenderThread( String discoveryAddress, int discoveryPort, String myHostName, int myPort,
                                    ArrayList cacheNames )
    {
        this.discoveryAddress = discoveryAddress;
        this.discoveryPort = discoveryPort;

        this.myHostName = myHostName;
        this.myPort = myPort;

        this.cacheNames = cacheNames;

        if ( log.isDebugEnabled() )
        {
            log.debug( "Creating sender thread for discoveryAddress = [" + discoveryAddress + "] and discoveryPort = ["
                + discoveryPort + "] myHostName = [" + myHostName + "] and port = [" + myPort + "]" );
        }

        try
        {
            // move this to the run method and determine how often to call it.
            UDPDiscoverySender sender = new UDPDiscoverySender( discoveryAddress, discoveryPort );
            sender.requestBroadcast();

            if ( log.isDebugEnabled() )
            {
                log.debug( "Sent a request broadcast to the group" );
            }
        }
        catch ( Exception e )
        {
            log.error( "Problem sending a Request Broadcast", e );
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    public void run()
    {
        try
        {
            // create this connection each time.
            // more robust
            UDPDiscoverySender sender = new UDPDiscoverySender( discoveryAddress, discoveryPort );

            sender.passiveBroadcast( myHostName, myPort, cacheNames );

            // todo we should consider sending a request broadcast every so
            // often.

            if ( log.isDebugEnabled() )
            {
                log.debug( "Called sender to issue a passive broadcast" );
            }

        }
        catch ( Exception e )
        {
            log.error( "Problem calling the UDP Discovery Sender", e );
        }
    }
}
