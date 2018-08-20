/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.felix.scr.Component;
import org.apache.felix.scr.Reference;
import org.apache.felix.scr.ScrInfo;
import org.apache.felix.scr.ScrService;
import org.apache.felix.scr.impl.config.ScrConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The <code>ScrCommand</code> class provides the implementations for the
 * Apache Felix Gogo and legacy Apache Felix Shell commands. The
 * {@link #register(BundleContext, ScrService, ScrConfiguration)} method
 * instantiates and registers the Gogo and Shell commands as possible.
 */
public class ScrCommand implements ScrInfo
{

    private final BundleContext bundleContext;
    private final ScrService scrService;
    private final ScrConfiguration scrConfiguration;

    private ServiceRegistration reg;

    static ScrCommand register(BundleContext bundleContext, ScrService scrService, ScrConfiguration scrConfiguration)
    {
        final ScrCommand cmd = new ScrCommand(bundleContext, scrService, scrConfiguration);

        /*
         * Register the Gogo Command as a service of its own class.
         * Due to a race condition during project building (this class is
         * compiled for Java 1.3 compatibility before the required
         * ScrGogoCommand class compiled for Java 5 compatibility) this uses
         * reflection to load and instantiate the class. Any failure during this
         * process is just ignored.
         */
        try
        {
            final String scrGogoCommandClassName = "org.apache.felix.scr.impl.ScrGogoCommand";
            final Class scrGogoCommandClass = scrService.getClass().getClassLoader().loadClass(scrGogoCommandClassName);
            final Constructor c = scrGogoCommandClass.getConstructor(new Class[]
                { ScrCommand.class });
            final Object gogoCmd = c.newInstance(new Object[]
                { cmd });
            final Hashtable props = new Hashtable();
            props.put("osgi.command.scope", "scr");
            props.put("osgi.command.function", new String[]
                { "config", "disable", "enable", "info", "list" });
            props.put(Constants.SERVICE_DESCRIPTION, "SCR Gogo Shell Support");
            props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
            bundleContext.registerService(scrGogoCommandClassName, gogoCmd, props);
        }
        catch (Throwable t)
        {
            // Might be thrown if running in a pre-Java 5 VM
        }

        // We dynamically import the impl service API, so it
        // might not actually be available, so be ready to catch
        // the exception when we try to register the command service.
        try
        {
            // Register "scr" impl command service as a
            // wrapper for the bundle repository service.
            final Hashtable props = new Hashtable();
            props.put(Constants.SERVICE_DESCRIPTION, "SCR Legacy Shell Support");
            props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
            bundleContext.registerService(org.apache.felix.shell.Command.class.getName(), new ScrShellCommand(cmd),
                props);
        }
        catch (Throwable th)
        {
            // Ignore.
        }
        return cmd;
    }

    private ScrCommand(BundleContext bundleContext, ScrService scrService, ScrConfiguration scrConfiguration)
    {
        this.bundleContext = bundleContext;
        this.scrService = scrService;
        this.scrConfiguration = scrConfiguration;
    }

    // ---------- Actual implementation


    public void update( boolean infoAsService )
    {
        if (infoAsService)
        {
            if ( reg == null )
            {
                final Hashtable props = new Hashtable();
                props.put(Constants.SERVICE_DESCRIPTION, "SCR Info service");
                props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
                reg = bundleContext.registerService( ScrInfo.class, this, props );
            }
        }
        else
        {
            if ( reg != null )
            {
                reg.unregister();
                reg = null;
            }
        }
    }

    /* (non-Javadoc)
     * @see org.apache.felix.scr.impl.ScrInfo#list(java.lang.String, java.io.PrintStream, java.io.PrintStream)
     */
    public void list(final String bundleIdentifier, final PrintWriter out)
    {
        Component[] components;

        if (bundleIdentifier != null)
        {
            Bundle bundle = null;
            try
            {
                long bundleId = Long.parseLong(bundleIdentifier);
                bundle = bundleContext.getBundle(bundleId);
            }
            catch (NumberFormatException nfe)
            {
                // might be a bundle symbolic name
                Bundle[] bundles = bundleContext.getBundles();
                for (int i = 0; i < bundles.length; i++)
                {
                    if (bundleIdentifier.equals(bundles[i].getSymbolicName()))
                    {
                        bundle = bundles[i];
                        break;
                    }
                }
            }

            if (bundle == null)
            {
                throw new IllegalArgumentException("Missing bundle with ID " + bundleIdentifier);
            }
            if (ComponentRegistry.isBundleActive(bundle))
            {
                components = scrService.getComponents(bundle);
                if (components == null)
                {
                    out.println("Bundle " + bundleIdentifier + " declares no components");
                    return;
                }
            }
            else
            {
                out.println("Bundle " + bundleIdentifier + " is not active");
                return;
            }
        }
        else
        {
            components = scrService.getComponents();
            if (components == null)
            {
                out.println("No components registered");
                return;
            }
        }

        Arrays.sort( components, new Comparator<Component>()
                {

                    public int compare(Component c1, Component c2)
                    {
                        return Long.signum(c1.getId() - c2.getId());
                    }

                });

        out.println(" Id   State BundleId Name");
        for ( Component component : components )
        {
            out.println( String.format( "[%1$4d] [%2$s] [%3$4d] %4$s", component.getId(), toStateString( component.getState() ), component.getBundle().getBundleId(), component.getName() ) );
        }
        out.flush();
   }

    /* (non-Javadoc)
     * @see org.apache.felix.scr.impl.ScrInfo#info(java.lang.String, java.io.PrintStream, java.io.PrintStream)
     */
    public void info(final String componentId, PrintWriter out)
    {
        Component[] components = getComponentFromArg(componentId);
        if (components == null)
        {
            return;
        }

        Arrays.sort( components, new Comparator<Component>()
                {

                    public int compare(Component c1, Component c2)
                    {
                        long bundleId1 = c1.getBundle().getBundleId();
                        long bundleId2 = c2.getBundle().getBundleId();
                        int result = Long.signum(bundleId1 - bundleId2);
                        if (result == 0) {
                            result = Long.signum(c1.getId() - c2.getId());
                        }
                        return result;
                    }

                });

        long bundleId = -1;

        for ( Component component : components )
        {
            if (components.length > 1)
            {
                if ( component.getBundle().getBundleId() != bundleId )
                {
                    if ( bundleId != -1 )
                    {
                        out.println();
                        out.println();
                    }
                    bundleId = component.getBundle().getBundleId();
                    out.println(String.format("*** Bundle: %1$s (%2$d)", component.getBundle().getSymbolicName(), bundleId));
                }
                out.println();
            }
            out.print( "ID: " );
            out.println( component.getId() );
            out.print( "Name: " );
            out.println( component.getName() );
            out.print( "Bundle: " );
            out.println( component.getBundle().getSymbolicName() + " (" + component.getBundle().getBundleId() + ")" );
            out.print( "State: " );
            out.println( toStateString( component.getState() ) );
            out.print( "Default State: " );
            out.println( component.isDefaultEnabled() ? "enabled" : "disabled" );
            out.print( "Activation: " );
            out.println( component.isImmediate() ? "immediate" : "delayed" );

            // DS 1.1 new features
            out.print( "Configuration Policy: " );
            out.println( component.getConfigurationPolicy() );
            out.print( "Activate Method: " );
            out.print( component.getActivate() );
            if ( component.isActivateDeclared() )
            {
                out.print( " (declared in the descriptor)" );
            }
            out.println();
            out.print( "Deactivate Method: " );
            out.print( component.getDeactivate() );
            if ( component.isDeactivateDeclared() )
            {
                out.print( " (declared in the descriptor)" );
            }
            out.println();
            out.print( "Modified Method: " );
            if ( component.getModified() != null )
            {
                out.print( component.getModified() );
            }
            else
            {
                out.print( "-" );
            }
            out.println();

            out.print( "Configuration Pid: " );
            out.print( component.getConfigurationPid() );
            if ( component.isConfigurationPidDeclared() )
            {
                out.print( " (declared in the descriptor)" );
            }
            out.println();

            if ( component.getFactory() != null )
            {
                out.print( "Factory: " );
                out.println( component.getFactory() );
            }

            String[] services = component.getServices();
            if ( services != null )
            {
                out.print( "Services: " );
                for ( String service: services )
                {
                    out.print( "          " );
                    out.println( service );
                }
                out.print( "Service Type: " );
                out.println( component.isServiceFactory() ? "service factory" : "service" );
            }

            Reference[] refs = component.getReferences();
            if ( refs != null )
            {
                for ( Reference ref : refs )
                {
                    out.print( "Reference: " );
                    out.println( ref.getName() );
                    out.print( "    Satisfied: " );
                    out.println( ref.isSatisfied() ? "satisfied" : "unsatisfied" );
                    out.print( "    Service Name: " );
                    out.println( ref.getServiceName() );
                    if ( ref.getTarget() != null )
                    {
                        out.print( "    Target Filter: " );
                        out.println( ref.getTarget() );
                    }
                    out.print( "    Multiple: " );
                    out.println( ref.isMultiple() ? "multiple" : "single" );
                    out.print( "    Optional: " );
                    out.println( ref.isOptional() ? "optional" : "mandatory" );
                    out.print( "    Policy: " );
                    out.println( ref.isStatic() ? "static" : "dynamic" );
                    out.print( "    Policy option: " );
                    out.println( ref.isReluctant() ? "reluctant" : "greedy" );
                    ServiceReference[] serviceRefs = ref.getServiceReferences();
                    if ( serviceRefs != null )
                    {
                        out.print( "    Bound to:" );
                        for ( int k = 0; k < serviceRefs.length; k++ )
                        {
                            out.print( "        " );
                            out.println( serviceRefs[k] );
                        }
                    }
                    else
                    {
                        out.println( "    (unbound)" );
                    }
                }
            }

            Dictionary props = component.getProperties();
            if ( props != null )
            {
                out.println( "Properties:" );
                TreeSet keys = new TreeSet( Collections.list( props.keys() ) );
                for ( Object key : keys )
                {
                    out.print( "    " );
                    out.print( key );
                    out.print( " = " );

                    Object prop = props.get( key );
                    if ( prop.getClass().isArray() )
                    {
                        prop = Arrays.asList( ( Object[] ) prop );
                    }
                    out.print( prop );

                    out.println();
                }
            }
        }
        out.flush();
    }

    void change(final String componentIdentifier, PrintWriter out, boolean enable)
    {
        Component[] components = getComponentFromArg(componentIdentifier);
        ArrayList<String> disposed = new ArrayList<String>();
        if (components == null)
        {
            return;
        }

        for ( Component component : components )
        {
            if ( component.getState() == Component.STATE_DISPOSED )
            {
                disposed.add(component.getName());
            }
            else if ( enable )
            {
                if ( component.getState() == Component.STATE_DISABLED )
                {
                    component.enable();
                    out.println( "Component " + component.getName() + " enabled" );
                }
                else
                {
                    out.println( "Component " + component.getName() + " already enabled" );
                }
            }
            else
            {
                if ( component.getState() != Component.STATE_DISABLED )
                {
                    component.disable();
                    out.println( "Component " + component.getName() + " disabled" );
                }
                else
                {
                    out.println( "Component " + component.getName() + " already disabled" );
                }
            }
        }
        out.flush();
        if ( !disposed.isEmpty() )
        {
            throw new IllegalArgumentException( "Components " + disposed + " already disposed, cannot change state" );

        }
    }

    /* (non-Javadoc)
     * @see org.apache.felix.scr.impl.ScrInfo#config(java.io.PrintStream)
     */
    public void config(PrintWriter out)
    {
        out.print("Log Level: ");
        out.println(scrConfiguration.getLogLevel());
        out.print("Component Factory with Factory Configuration: ");
        out.println(scrConfiguration.isFactoryEnabled() ? "Supported" : "Unsupported");
        out.print("Keep instances with no references: ");
        out.println(scrConfiguration.keepInstances() ? "Supported" : "Unsupported");
        out.print("Lock timeount milliseconds: ");
        out.println(scrConfiguration.lockTimeout());
        out.print("Info Service registered: ");
        out.println(scrConfiguration.infoAsService() ? "Supported" : "Unsupported");
    }

    private String toStateString(int state)
    {
        switch (state) {

        case (Component.STATE_DISABLED):
            return "disabled    ";
        case (Component.STATE_ENABLING):
            return "enabling    ";
        case (Component.STATE_ENABLED):
            return "enabled     ";
        case (Component.STATE_UNSATISFIED):
            return "unsatisfied ";
        case (Component.STATE_ACTIVATING):
            return "activating  ";
        case (Component.STATE_ACTIVE):
            return "active      ";
        case (Component.STATE_REGISTERED):
            return "registered  ";
        case (Component.STATE_FACTORY):
            return "factory     ";
        case (Component.STATE_DEACTIVATING):
            return "deactivating";
        case (Component.STATE_DISABLING):
            return "disabling   ";
        case (Component.STATE_DISPOSING):
            return "disposing   ";
        case (Component.STATE_DISPOSED):
            return "disposed    ";
        default:
            return "unkown: " + state;
        }
    }

    private Component[] getComponentFromArg(final String componentIdentifier)
    {
        Component[] components = null;
        if (componentIdentifier != null)
        {
            try
            {
                long componentId = Long.parseLong(componentIdentifier);
                Component component = scrService.getComponent(componentId);
                if (component == null)
                {
                    throw new IllegalArgumentException("Missing Component with ID " + componentId);
                }
                else
                {
                    return new Component[]
                        { component };
                }
            }
            catch (NumberFormatException nfe)
            {

                // check whether it is a component name
                components = scrService.getComponents(componentIdentifier);
            }
        }
        if ( components == null)
        {
            components = scrService.getComponents();
            if (componentIdentifier != null)
            {
                ArrayList<Component> cs = new ArrayList<Component>(components.length);
                Pattern p = Pattern.compile(componentIdentifier);
                for (Component component: components)
                {
                    if ( p.matcher( component.getName()).matches() )
                    {
                        cs.add( component );
                    }
                }
                if (cs.isEmpty())
                {
                    throw new IllegalArgumentException("No Component with ID or matching " + componentIdentifier);
                }
                components = cs.toArray( new Component[cs.size()] );
            }
        }

        return components;
    }

}
