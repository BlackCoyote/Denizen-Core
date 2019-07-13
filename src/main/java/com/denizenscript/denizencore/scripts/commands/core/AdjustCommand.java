package com.denizenscript.denizencore.scripts.commands.core;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.dB;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.tags.core.UtilTags;

import java.util.HashMap;
import java.util.function.Consumer;


public class AdjustCommand extends AbstractCommand {

    // <--[command]
    // @Name Adjust
    // @Syntax adjust [<dObject>/def:<name>|...] [<mechanism>](:<value>)
    // @Required 2
    // @Short Adjusts a dObjects mechanism.
    // @Group core
    // @Video /denizen/vids/Properties%20and%20Mechanisms
    //
    // @Description
    // Many dObjects contains options and properties that need to be adjusted. Denizen employs a mechanism
    // interface to deal with those adjustments. To easily accomplish this, use this command with a valid object
    // mechanism, and sometimes accompanying value.
    //
    // Specify "def:<name>" as an input to adjust a definition and automatically save the result back to the definition.
    //
    // To adjust an item in an inventory, use <@link command inventory>, as '- inventory adjust slot:<#> <mechanism>:<value>'.
    //
    // @Tags
    // <entry[saveName].result> returns the adjusted object.
    // <entry[saveName].result_list> returns a dList of adjusted objects.
    //
    // @Usage
    // Use to set a custom display name on an entity.
    // - adjust e@1000 'custom_name:ANGRY!'
    //
    // @Usage
    // Use to set the skin of every online player.
    // - adjust <server.list_online_players> skin:mcmonkey4eva
    //
    // @Usage
    // Use to modify an item held in a definition.
    // - adjust def:stick "display_name:Fancy stick"
    //
    // -->

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {

        for (aH.Argument arg : aH.interpretArguments(scriptEntry.aHArgs)) {
            if (!scriptEntry.hasObject("object")) {
                if (arg.object instanceof dList) {
                    scriptEntry.addObject("object", arg.object);
                }
                else if (arg.object instanceof Element) {
                    // Special parse to avoid prefixing issues
                    scriptEntry.addObject("object", dList.valueOf(arg.raw_value));
                }
                else {
                    scriptEntry.addObject("object", arg.asType(dList.class));
                }
            }
            else if (!scriptEntry.hasObject("mechanism")) {
                if (arg.hasPrefix()) {
                    scriptEntry.addObject("mechanism", new Element(arg.getPrefix().getValue()));
                    scriptEntry.addObject("mechanism_value", arg.asElement());
                }
                else {
                    scriptEntry.addObject("mechanism", arg.asElement());
                }

            }
            else {
                arg.reportUnhandled();
            }
        }

        if (!scriptEntry.hasObject("object")) {
            throw new InvalidArgumentsException("You must specify an object!");
        }

        if (!scriptEntry.hasObject("mechanism")) {
            throw new InvalidArgumentsException("You must specify a mechanism!");
        }
    }

    public static HashMap<String, Consumer<Mechanism>> specialAdjustables = new HashMap<>();

    static {
        specialAdjustables.put("system", UtilTags::adjustSystem);
    }

    public dObject adjust(dObject object, Element mechanismName, Element value, ScriptEntry entry) {
        Mechanism mechanism = new Mechanism(mechanismName, value, entry.entryData.getTagContext());
        return adjust(object, mechanism, entry);
    }

    public dObject adjust(dObject object, Mechanism mechanism, ScriptEntry entry) {
        String objectString = object.toString();
        String lowerObjectString = CoreUtilities.toLowerCase(objectString);
        Consumer<Mechanism> specialAdjustable = specialAdjustables.get(lowerObjectString);
        if (specialAdjustable != null) {
            specialAdjustable.accept(mechanism);
            return object;
        }
        if (lowerObjectString.startsWith("def:")) {
            String defName = lowerObjectString.substring("def:".length());
            dObject def = entry.getResidingQueue().getDefinitionObject(defName);
            if (def == null) {
                dB.echoError("Invalid definition name '" + defName + "', cannot adjust");
                return object;
            }
            def = adjust(def, mechanism, entry);
            entry.getResidingQueue().addDefinition(defName, def);
            return def;
        }
        if (object instanceof Element) {
            object = ObjectFetcher.pickObjectFor(objectString, entry.entryData.getTagContext());
            if (object instanceof Element) {
                dB.echoError("Unable to determine what object to adjust (missing object notation?), for: " + objectString);
                return object;
            }
        }
        if (object instanceof dList) {
            dList subList = (dList) object;
            dList result = new dList();
            for (dObject listObject : subList.objectForms) {
                listObject = adjust(listObject, mechanism, entry);
                result.addObject(listObject);
            }
            return result;
        }
        // Make sure this object is Adjustable
        if (!(object instanceof Adjustable)) {
            dB.echoError("'" + objectString + "' is not an adjustable object type.");
            return object;
        }
        ((Adjustable) object).safeAdjust(mechanism);
        return object;
    }


    @Override
    public void execute(ScriptEntry scriptEntry) {

        Element mechanism = scriptEntry.getElement("mechanism");
        Element value = scriptEntry.getElement("mechanism_value");

        dList objects = scriptEntry.getdObject("object");

        if (scriptEntry.dbCallShouldDebug()) {
            dB.report(scriptEntry, getName(),
                    objects.debug()
                            + mechanism.debug()
                            + (value == null ? "" : value.debug()));
        }

        dList result = new dList();

        for (dObject object : objects.objectForms) {
            object = adjust(object, mechanism, value, scriptEntry);
            if (objects.size() == 1) {
                scriptEntry.addObject("result", object);
            }
            result.addObject(object);
        }

        scriptEntry.addObject("result_list", result);

    }
}