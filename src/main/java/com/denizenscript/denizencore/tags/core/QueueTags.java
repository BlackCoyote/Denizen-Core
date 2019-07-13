package com.denizenscript.denizencore.tags.core;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.TagRunnable;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ReplaceableTagEvent;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.SlowWarning;
import com.denizenscript.denizencore.tags.TagManager;

public class QueueTags {

    public QueueTags() {
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                queueTag(event);
            }
        }, "queue", "q");
    }


    //////////
    //  ReplaceableTagEvent handler
    ////////

    public SlowWarning queueShorthand = new SlowWarning("Short-named tags are hard to read. Please use 'queue' instead of 'q' as a root tag.");

    public void queueTag(ReplaceableTagEvent event) {

        if (!event.matches("queue", "q")) {
            return;
        }

        if (event.matches("q")) {
            queueShorthand.warn(event.getScriptEntry());
        }

        // Handle <queue[id]. ...> tags

        if (event.hasNameContext()) {
            if (!ScriptQueue._queueExists(event.getNameContext())) {
                return;
            }
            else {
                event.setReplacedObject(CoreUtilities.autoAttrib(ScriptQueue._getExistingQueue(event.getNameContext())
                        , event.getAttributes().fulfill(1)));
            }
            return;
        }

        Attribute attribute = event.getAttributes().fulfill(1);


        // Otherwise, try to use queue in a static manner.

        // <--[tag]
        // @attribute <queue.exists[<queue_id>]>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether the specified queue exists.
        // -->
        if (attribute.startsWith("exists")
                && attribute.hasContext(1)) {
            event.setReplacedObject(CoreUtilities.autoAttrib(new ElementTag(ScriptQueue._queueExists(attribute.getContext(1)))
                    , attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <queue.stats>
        // @returns ElementTag
        // @description
        // Returns stats for all queues during this server session
        // -->
        if (attribute.startsWith("stats")) {
            event.setReplacedObject(CoreUtilities.autoAttrib(new ElementTag(ScriptQueue._getStats())
                    , attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <queue.list>
        // @returns ListTag(Queue)
        // @description
        // Returns a list of all currently running queues on the server.
        // -->
        if (attribute.startsWith("list")) {
            event.setReplacedObject(CoreUtilities.autoAttrib(new ListTag(ScriptQueue._getQueues())
                    , attribute.fulfill(1)));
            return;
        }


        // Else,
        // Use current queue

        event.setReplacedObject(CoreUtilities.autoAttrib(event.getScriptEntry().getResidingQueue()
                , attribute));
    }
}


