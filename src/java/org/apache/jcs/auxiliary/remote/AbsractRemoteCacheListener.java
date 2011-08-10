package org.apache.jcs.auxiliary.remote;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheAttributes;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheListener;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheElementSerialized;
import org.apache.jcs.engine.behavior.ICompositeCacheManager;
import org.apache.jcs.engine.behavior.IElementSerializer;
import org.apache.jcs.engine.control.CompositeCache;
import org.apache.jcs.engine.control.CompositeCacheManager;
import org.apache.jcs.utils.net.HostNameUtil;
import org.apache.jcs.utils.serialization.SerializationConversionUtil;
import org.apache.jcs.utils.serialization.StandardSerializer;

/** Shared listener base. */
public abstract class AbsractRemoteCacheListener
    implements IRemoteCacheListener
{
    /** Don't change */
    private static final long serialVersionUID = 32442324243243L;

    /** The logger */
    private final static Log log = LogFactory.getLog( AbsractRemoteCacheListener.class );

    /** The cached name of the local host. The remote server gets this for logging purposes. */
    private static String localHostName = null;

    /** Has this client been shutdown. */
    boolean disposed = false;

    /**
     * The cache manager used to put items in different regions. This is set lazily and should not
     * be sent to the remote server.
     */
    protected transient ICompositeCacheManager cacheMgr;

    /** The remote cache configuration object. */
    protected IRemoteCacheAttributes irca;

    /** Number of put requests received. For debugging only. */
    protected int puts = 0;

    /** Number of remove requests received. For debugging only. */
    protected int removes = 0;

    /** This is set by the remote cache server. */
    protected long listenerId = 0;

    /** Custom serializer. Standard by default. */
    private transient IElementSerializer elementSerializer = new StandardSerializer();

    /**
     * Only need one since it does work for all regions, just reference by multiple region names.
     * <p>
     * The constructor exports this object, making it available to receive incoming calls. The
     * callback port is anonymous unless a local port value was specified in the configuration.
     * <p>
     * @param irca
     * @param cacheMgr
     */
    public AbsractRemoteCacheListener( IRemoteCacheAttributes irca, ICompositeCacheManager cacheMgr )
    {
        this.irca = irca;
        this.cacheMgr = cacheMgr;
    }

    /**
     * Let the remote cache set a listener_id. Since there is only one listerenr for all the regions
     * and every region gets registered? the id shouldn't be set if it isn't zero. If it is we
     * assume that it is a reconnect.
     * <p>
     * @param id The new listenerId value
     * @throws IOException
     */
    public void setListenerId( long id )
        throws IOException
    {
        listenerId = id;
        if ( log.isInfoEnabled() )
        {
            log.info( "set listenerId = [" + id + "]" );
        }
    }

    /**
     * Gets the listenerId attribute of the RemoteCacheListener object. This is stored in the
     * object. The RemoteCache object contains a reference to the listener and get the id this way.
     * <p>
     * @return The listenerId value
     * @throws IOException
     */
    public long getListenerId()
        throws IOException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "get listenerId = [" + listenerId + "]" );
        }
        return listenerId;

    }

    /**
     * Gets the remoteType attribute of the RemoteCacheListener object <p.
     * @return The remoteType value
     * @throws IOException
     */
    public int getRemoteType()
        throws IOException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "getRemoteType = [" + irca.getRemoteType() + "]" );
        }
        return irca.getRemoteType();
    }

    /**
     * If this is configured to remove on put, then remove the element since it has been updated
     * elsewhere. cd should be incomplete for faster transmission. We don't want to pass data only
     * invalidation. The next time it is used the local cache will get the new version from the
     * remote store.
     * <p>
     * If remove on put is not configured, then update the item.
     * @param cb
     * @throws IOException
     */
    public void handlePut( ICacheElement cb )
        throws IOException
    {
        if ( irca.getRemoveUponRemotePut() )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "PUTTING ELEMENT FROM REMOTE, (  invalidating ) " );
            }
            handleRemove( cb.getCacheName(), cb.getKey() );
        }
        else
        {
            puts++;
            if ( log.isDebugEnabled() )
            {
                log.debug( "PUTTING ELEMENT FROM REMOTE, ( updating ) " );
                log.debug( "cb = " + cb );

                if ( puts % 100 == 0 )
                {
                    log.debug( "puts = " + puts );
                }
            }

            ensureCacheManager();
            CompositeCache cache = cacheMgr.getCache( cb.getCacheName() );

            // Eventually the instance of will not be necessary.
            if ( cb instanceof ICacheElementSerialized )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( "Object needs to be deserialized." );
                }
                try
                {
                    cb = SerializationConversionUtil.getDeSerializedCacheElement( (ICacheElementSerialized) cb,
                                                                                  this.elementSerializer );
                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "Deserialized result = " + cb );
                    }
                }
                catch ( IOException e )
                {
                    throw e;
                }
                catch ( ClassNotFoundException e )
                {
                    log.error( "Received a serialized version of a class that we don't know about.", e );
                }
            }

            cache.localUpdate( cb );
        }

        return;
    }

    /**
     * Calls localRemove on the CompositeCache.
     * <p>
     * @param cacheName
     * @param key
     * @throws IOException
     */
    public void handleRemove( String cacheName, Serializable key )
        throws IOException
    {
        removes++;
        if ( log.isDebugEnabled() )
        {
            if ( removes % 100 == 0 )
            {
                log.debug( "removes = " + removes );
            }

            log.debug( "handleRemove> cacheName=" + cacheName + ", key=" + key );
        }

        ensureCacheManager();
        CompositeCache cache = cacheMgr.getCache( cacheName );

        cache.localRemove( key );
    }

    /**
     * Calls localRemoveAll on the CompositeCache.
     * <p>
     * @param cacheName
     * @throws IOException
     */
    public void handleRemoveAll( String cacheName )
        throws IOException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "handleRemoveAll> cacheName=" + cacheName );
        }
        ensureCacheManager();
        CompositeCache cache = cacheMgr.getCache( cacheName );
        cache.localRemoveAll();
    }

    /**
     * @param cacheName
     * @throws IOException
     */
    public void handleDispose( String cacheName )
        throws IOException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "handleDispose> cacheName=" + cacheName );
        }
        // TODO consider what to do here, we really don't want to
        // dispose, we just want to disconnect.
        // just allow the cache to go into error recovery mode.
        // getCacheManager().freeCache( cacheName, true );
    }

    /**
     * Gets the cacheManager attribute of the RemoteCacheListener object. This is one of the few
     * places that force the cache to be a singleton.
     */
    protected void ensureCacheManager()
    {
        if ( cacheMgr == null )
        {
            cacheMgr = CompositeCacheManager.getInstance();
            log.debug( "had to get cacheMgr" );
            if ( log.isDebugEnabled() )
            {
                log.debug( "cacheMgr = " + cacheMgr );
            }
        }
        else
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "already got cacheMgr = " + cacheMgr );
            }
        }
    }

    /**
     * This is for debugging. It allows the remote server to log the address of clients.
     * <p>
     * @return String
     * @throws IOException
     */
    public synchronized String getLocalHostAddress()
        throws IOException
    {
        if ( localHostName == null )
        {
            try
            {
                localHostName = HostNameUtil.getLocalHostAddress();
            }
            catch ( UnknownHostException uhe )
            {
                localHostName = "unknown";
            }
        }
        return localHostName;
    }

    /**
     * For easier debugging.
     * <p>
     * @return Basic info on this listener.
     */
    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append( "\n AbstractRemoteCacheListener: " );
        buf.append( "\n RemoteHost = " + irca.getRemoteHost() );
        buf.append( "\n RemotePort = " + irca.getRemotePort() );
        buf.append( "\n ListenerId = " + listenerId );
        return buf.toString();
    }
}