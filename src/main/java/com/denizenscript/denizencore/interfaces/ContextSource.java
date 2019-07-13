package com.denizenscript.denizencore.interfaces;

import com.denizenscript.denizencore.objects.dObject;

/**
 * Provides contexts to a queue.
 */
public interface ContextSource {

    public boolean getShouldCache();

    public dObject getContext(String name);
}