/*
 * This file is part of IPAACA, the
 *  "Incremental Processing Architecture
 *   for Artificial Conversational Agents".
 *
 * Copyright (c) 2009-2013 Sociable Agents Group
 *                         CITEC, Bielefeld University
 *
 * http://opensource.cit-ec.de/projects/ipaaca/
 * http://purl.org/net/ipaaca
 *
 * This file may be licensed under the terms of of the
 * GNU Lesser General Public License Version 3 (the ``LGPL''),
 * or (at your option) any later version.
 *
 * Software distributed under the License is distributed
 * on an ``AS IS'' basis, WITHOUT WARRANTY OF ANY KIND, either
 * express or implied. See the LGPL for the specific language
 * governing rights and limitations.
 *
 * You should have received a copy of the LGPL along with this
 * program. If not, go to http://www.gnu.org/licenses/lgpl.html
 * or write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * The development of this software was supported by the
 * Excellence Cluster EXC 277 Cognitive Interaction Technology.
 * The Excellence Cluster EXC 277 is a grant of the Deutsche
 * Forschungsgemeinschaft (DFG) in the context of the German
 * Excellence Initiative.
 */

package ipaaca;

import ipaaca.protobuf.Ipaaca.IUCommission;
import ipaaca.protobuf.Ipaaca.IURetraction;
import ipaaca.protobuf.Ipaaca.IUResendRequest;
import ipaaca.protobuf.Ipaaca.IULinkUpdate;
import ipaaca.protobuf.Ipaaca.IUPayloadUpdate;
import ipaaca.protobuf.Ipaaca.PayloadItem;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsb.Event;
import rsb.Factory;
import rsb.Handler;
import rsb.InitializeException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import rsb.Listener;
import rsb.RSBException;
import rsb.Scope;
import rsb.patterns.RemoteServer;

/**
 * An InputBuffer that holds remote IUs.
 * @author hvanwelbergen
 */
@Slf4j
public class InputBuffer extends Buffer
{
    private Map<String, RemoteServer> remoteServerStore = new HashMap<String, RemoteServer>();
    private Map<String, Listener> listenerStore = new HashMap<String, Listener>();
    private Set<String> categoryInterests = new HashSet<String>();
    private final static Logger logger = LoggerFactory.getLogger(InputBuffer.class.getName());
    private IUStore<RemotePushIU> iuStore = new IUStore<RemotePushIU>();
    private IUStore<RemoteMessageIU> messageStore = new IUStore<RemoteMessageIU>();

    private String channel = "default";
    private boolean resendActive;

    public void close()
    {
        for (Listener listener : listenerStore.values())
        {
            try
            {
                listener.deactivate();
            }
            catch (RSBException e)
            {
                log.warn("RSB Exception on deactive {}", e, listener.toString());
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        for (RemoteServer remServer : remoteServerStore.values())
        {
            try
            {
                remServer.deactivate();
            }
            catch (RSBException e)
            {
                log.warn("RSB Exception on RemoteServer deactivate {}", e, remServer.toString());
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    // def __init__(self, owning_component_name, category_interests=None, participant_config=None):
    // '''Create an InputBuffer.
    //
    // Keyword arguments:
    // owning_compontent_name -- name of the entity that owns this InputBuffer
    // category_interests -- list of IU categories this Buffer is interested in
    // participant_config = RSB configuration
    // '''
    // super(InputBuffer, self).__init__(owning_component_name, participant_config)
    // self._unique_name = '/ipaaca/component/'+str(owning_component_name)+'ID'+self._uuid+'/IB'
    // self._listener_store = {} # one per IU category
    // self._remote_server_store = {} # one per remote-IU-owning Component
    // self._category_interests = []
    // if category_interests is not None:
    // for cat in category_interests:
    // self._create_category_listener_if_needed(cat)
    public InputBuffer(String owningComponentName, Set<String> categoryInterests)
    {
        this(owningComponentName, categoryInterests, "default");
    }


    public InputBuffer(String owningComponentName, Set<String> categoryInterests, String ipaaca_channel)
    {
        super(owningComponentName);
        resendActive = false;
        String shortIDName = getUniqueShortName();
        uniqueName = "/ipaaca/component/" + shortIDName + "/IB";

        this.channel = ipaaca_channel;
        
        for (String cat : categoryInterests)
        {
            createCategoryListenerIfNeeded(cat);
        }

    // add own uuid as identifier for hidden channel. (dlw)
    createCategoryListenerIfNeeded(shortIDName);
    }

    /** Pass resendActive to toggle resendRequest-functionality. */
    public InputBuffer(BufferConfiguration bufferconfiguration)
    {
        super(bufferconfiguration.getOwningComponentName());
        this.resendActive = bufferconfiguration.getResendActive();
        String shortIDName = getUniqueShortName();
        uniqueName = "/ipaaca/component/" + shortIDName + "/IB";

        for (String cat : bufferconfiguration.getCategoryInterests())
        {
            createCategoryListenerIfNeeded(cat);
        }
        this.channel = bufferconfiguration.getChannel();

        // add own uuid as identifier for hidden channel. (dlw)
        createCategoryListenerIfNeeded(shortIDName);
    }

    public boolean isResendActive() {
        return this.resendActive;
    }

    public void setResendActive(boolean active) {
        this.resendActive = active;
    }

    // def _get_remote_server(self, iu):
    // '''Return (or create, store and return) a remote server.'''
    // if iu.owner_name in self._remote_server_store:
    // return self._remote_server_store[iu.owner_name]
    // remote_server = rsb.createRemoteServer(rsb.Scope(str(iu.owner_name)))
    // self._remote_server_store[iu.owner_name] = remote_server
    // return remote_server
    protected RemoteServer getRemoteServer(AbstractIU iu)
    {
        if (remoteServerStore.containsKey(iu.getOwnerName()))
        {
            return remoteServerStore.get(iu.getOwnerName());
        }
        logger.debug("Getting remote server for {}", iu.getOwnerName());
        RemoteServer remoteServer = Factory.getInstance().createRemoteServer(new Scope(iu.getOwnerName()));
        try
        {
            remoteServer.activate();
        }
        catch (InitializeException e)
        {
            throw new RuntimeException(e);
        }
        catch (RSBException e)
        {
            throw new RuntimeException(e);
        }
        remoteServerStore.put(iu.getOwnerName(), remoteServer);
        return remoteServer;
    }

    protected RemoteServer getRemoteServer(String ownerName)
    {
        if (remoteServerStore.containsKey(ownerName))
        {
            return remoteServerStore.get(ownerName);
        }
        logger.debug("Getting remote server for {}", ownerName);
        RemoteServer remoteServer = Factory.getInstance().createRemoteServer(new Scope(ownerName));
        try
        {
            remoteServer.activate();
        }
        catch (InitializeException e)
        {
            throw new RuntimeException(e);
        }
        catch (RSBException e)
        {
            throw new RuntimeException(e);
        }
        remoteServerStore.put(ownerName, remoteServer);
        return remoteServer;
    }

    // def _create_category_listener_if_needed(self, iu_category):
    // '''Return (or create, store and return) a category listener.'''
    // if iu_category in self._listener_store: return self._informer_store[iu_category]
    // cat_listener = rsb.createListener(rsb.Scope("/ipaaca/category/"+str(iu_category)), config=self._participant_config)
    // cat_listener.addHandler(self._handle_iu_events)
    // self._listener_store[iu_category] = cat_listener
    // self._category_interests.append(iu_category)
    // logger.info("Added category listener for "+iu_category)
    // return cat_listener
    private Listener createCategoryListenerIfNeeded(String category)
    {
        if (listenerStore.containsKey(category))
        {
            return listenerStore.get(category);
        }
        Listener listener;
        try
        {
            listener = Factory.getInstance().createListener(new Scope("/ipaaca/channel/" + this.channel + "/category/" + category));
        }
        catch (InitializeException e1)
        {
            throw new RuntimeException(e1);
        }
        listenerStore.put(category, listener);
        categoryInterests.add(category);
        try
        {
            listener.addHandler(new InputHandler(), true);
        }
        catch (InterruptedException e1)
        {
            Thread.currentThread().interrupt();
        }
        logger.info("Added category listener for {}", category);
        try
        {
            listener.activate();
        }
        catch (InitializeException e)
        {
            throw new RuntimeException(e);
        }
        catch (RSBException e)
        {
            throw new RuntimeException(e);
        }
        return listener;
    }

    class InputHandler implements Handler
    {

        @Override
        public void internalNotify(Event ev)
        {
            handleIUEvents(ev);
        }

    }
    
    // def _handle_iu_events(self, event):
    // '''Dispatch incoming IU events.
    //
    // Adds incoming IU's to the store, applies payload and commit updates to
    // IU, calls IU event handlers.'
    //
    // Keyword arguments:
    // event -- a converted RSB event
    // '''
    // if type(event.data) is RemotePushIU:
    // # a new IU
    // if event.data.uid in self._iu_store:
    // # already in our store
    // pass
    // else:
    // self._iu_store[ event.data.uid ] = event.data
    // event.data.buffer = self
    // self.call_iu_event_handlers(event.data.uid, local=False, event_type=IUEventType.ADDED, category=event.data.category)
    // else:
    // # an update to an existing IU
    // if event.data.writer_name == self.unique_name:
    // # Discard updates that originate from this buffer
    // return
    // if event.data.uid not in self._iu_store:
    // # TODO: we should request the IU's owner to send us the IU
    // logger.warning("Update message for IU which we did not fully receive before.")
    // return
    // if type(event.data) is iuProtoBuf_pb2.IUCommission:
    // # IU commit
    // iu = self._iu_store[event.data.uid]
    // iu._apply_commission()
    // iu._revision = event.data.revision
    // self.call_iu_event_handlers(event.data.uid, local=False, event_type=IUEventType.COMMITTED, category=iu.category)
    // elif type(event.data) is IUPayloadUpdate:
    // # IU payload update
    // iu = self._iu_store[event.data.uid]
    // iu._apply_update(event.data)
    // self.call_iu_event_handlers(event.data.uid, local=False, event_type=IUEventType.UPDATED, category=iu.category)
    /**
     * Dispatch incoming IU events.
     */
    private void handleIUEvents(Event event)
    {
        if (event.getData() instanceof RemoteMessageIU)
        {
            RemoteMessageIU rm = (RemoteMessageIU) event.getData();
            if (messageStore.containsKey(rm.getUid())) {
                logger.warn("Spurious RemoteMessage event: already got this UID: "+rm.getUid());
                return;
            }
            //logger.info("Adding Message "+rm.getUid());
            messageStore.put(rm.getUid(), rm);
            //logger.info("Calling handlers for Message "+rm.getUid());
            callIuEventHandlers(rm.getUid(),false, IUEventType.MESSAGE, rm.getCategory());
            //logger.info("Removing Message "+rm.getUid());
            messageStore.remove(rm.getUid());
        }
        else if (event.getData() instanceof RemotePushIU)
        {
            RemotePushIU rp = (RemotePushIU) event.getData();
            // a new IU
            if (iuStore.containsKey(rp.getUid()))
            {
                // already in our store
                return;
            }
            else
            {
                iuStore.put(rp.getUid(), rp);
                rp.setBuffer(this);
                this.callIuEventHandlers(rp.getUid(), false, IUEventType.ADDED, rp.getCategory());
            }
        }
        else
        {
            if (event.getData() instanceof IULinkUpdate)
            {
                IULinkUpdate iuLinkUpdate = (IULinkUpdate) event.getData();
                if (iuLinkUpdate.getWriterName().equals(this.getUniqueName()))
                {
                    // Discard updates that originate from this buffer
                    return;
                }
                if (!iuStore.containsKey(iuLinkUpdate.getUid()))
                {
                    if (resendActive)
                    {
                        triggerResendRequest(event.getData(), getUniqueShortName());
                    } else {
                        logger.warn("Link update message for IU which we did not fully receive before.");
                    }
                    return;
                }
                RemotePushIU iu = this.iuStore.get(iuLinkUpdate.getUid());
                iu.applyLinkUpdate(iuLinkUpdate);
                callIuEventHandlers(iu.getUid(), false, IUEventType.LINKSUPDATED, iu.category);
            }
            if (event.getData() instanceof IUPayloadUpdate)
            {
                IUPayloadUpdate iuUpdate = (IUPayloadUpdate) event.getData();
                logger.debug("handleIUEvents invoked with an IUPayloadUpdate: {}", iuUpdate);
                if (iuUpdate.getWriterName().equals(this.getUniqueName()))
                {
                    // Discard updates that originate from this buffer
                    return;
                }
                if (!iuStore.containsKey(iuUpdate.getUid()))
                {
                    if (resendActive)
                    {
                        triggerResendRequest(event.getData(), getUniqueShortName());
                    } else {
                        logger.warn("Update message for IU which we did not fully receive before.");
                    }
                    return;
                }
                RemotePushIU iu = this.iuStore.get(iuUpdate.getUid());
                iu.applyUpdate(iuUpdate);
                callIuEventHandlers(iu.getUid(), false, IUEventType.UPDATED, iu.category);
            }
            if (event.getData() instanceof IUCommission)
            {
                IUCommission iuc = (IUCommission) event.getData();
                logger.debug("handleIUEvents invoked with an IUCommission: {}", iuc);
                logger.debug("{}, {}", iuc.getWriterName(), this.getUniqueName());

                if (iuc.getWriterName().equals(this.getUniqueName()))
                {
                    // Discard updates that originate from this buffer
                    return;
                }
                if (!iuStore.containsKey(iuc.getUid()))
                {
                    if (resendActive)
                    {
                        triggerResendRequest(event.getData(), getUniqueShortName());
                    } else {
                        logger.warn("Update message for IU which we did not fully receive before.");
                    }
                    return;
                }
                RemotePushIU iu = this.iuStore.get(iuc.getUid());
                iu.applyCommmision();
                iu.setRevision(iuc.getRevision());
                callIuEventHandlers(iuc.getUid(), false, IUEventType.COMMITTED, iu.getCategory());
            }
            if (event.getData() instanceof IURetraction)
            {
                IURetraction iuc = (IURetraction) event.getData();
                logger.debug("handleIUEvents invoked with an IURetraction: {}", iuc);
                logger.debug("{}", this.getUniqueName());

                if (!iuStore.containsKey(iuc.getUid()))
                {
                    logger.warn("Update message for IU which we did not fully receive before.");
                }
                RemotePushIU iu = this.iuStore.get(iuc.getUid());
                if (iu != null) {
                    iu.applyRetraction();
                    callIuEventHandlers(iuc.getUid(), false, IUEventType.RETRACTED, iu.getCategory());
                }
            }
        }
    }

    private void triggerResendRequest(Object aiuObj, String hiddenScopeName)
    {
        String uid = null;
        String writerName = null;
        if (aiuObj instanceof IULinkUpdate) {
            IULinkUpdate tmp = (IULinkUpdate)aiuObj;
            uid = tmp.getUid();
            writerName = tmp.getWriterName();
        } else if (aiuObj instanceof IUPayloadUpdate) {
            IUPayloadUpdate tmp = (IUPayloadUpdate)aiuObj;
            uid = tmp.getUid();
            writerName = tmp.getWriterName();
        } else if (aiuObj instanceof IUCommission) {
            IUCommission tmp = (IUCommission)aiuObj;
            uid = tmp.getUid();
            writerName = tmp.getWriterName();
        }
        RemoteServer rServer = null;
        if (writerName != null)
            rServer = getRemoteServer(writerName);
        if ((rServer != null)&&(uid != null)) {
            IUResendRequest iurr = IUResendRequest.newBuilder().setUid(uid).setHiddenScopeName(hiddenScopeName).build();
            long rRevision = 0;
            try
                {
                    rRevision = (Long) rServer.call("resendRequest", iurr);
                }
                catch (RSBException e)
                {
                    throw new RuntimeException(e);
                }
                catch (ExecutionException e)
                {
                    throw new RuntimeException(e);
                }
                catch (TimeoutException e)
                {
                    throw new RuntimeException(e);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            if (rRevision == 0)
            {
                //throw new IUResendFailedException(aiu); // TODO
            }
        }
    }

    public InputBuffer(String owningComponentName)
    {
        super(owningComponentName);
    }

    @Override
    public AbstractIU getIU(String iuid)
    {
        if (iuStore.get(iuid) != null)
        {
            return iuStore.get(iuid);
        }
        else
        {
            return messageStore.get(iuid);
        }
    }

    public void addCategoryInterest(String... categories)
    {
        for(String cat:categories)
        {
            createCategoryListenerIfNeeded(cat);
        }
    }

    public Collection<RemotePushIU> getIUs()
    {
        return iuStore.values();
    }

}
