package Main;
/*
Copyright (C) 2001, 2009 United States Government
as represented by the Administrator of the
National Aeronautics and Space Administration.
All Rights Reserved.
*/

import Utilities.StateVector;
import gov.nasa.worldwind.*;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.examples.ClickAndGoSelectListener;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.view.orbit.*;
import netscape.javascript.JSObject;
//Start of JSatTrak imports
import Bodies.*;
import Utilities.Time;
import java.util.Hashtable;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import Satellite.*;
import java.util.Vector;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import Layers.*;
import gov.nasa.worldwind.examples.sunlight.*;
import gov.nasa.worldwind.render.*;
import java.util.ArrayList;
import gov.nasa.worldwind.event.*;
import gov.nasa.worldwind.awt.AWTInputHandler;
import View.*;
import gov.nasa.worldwind.view.orbit.BasicOrbitView;
import java.util.GregorianCalendar;
import Utilities.OnlineInput;
import TwoDImage.*;

import javax.swing.*;
import java.awt.*;

import gov.nasa.worldwind.layers.placename.*;
import gov.nasa.worldwind.layers.Earth.*;
import Utilities.AstroConst;
import javax.swing.Timer;
import com.sun.opengl.util.JOGLAppletLauncher;

/**
 * Provides a base application framework for simple WorldWind applets.
 * <p/>
 * A simple applet which runs World Wind with a StatusBar at the bottom and lets javascript set some view attributes.
 *
 * @author Patrick Murris
 * @version $Id: WWJApplet.java 15441 2011-05-14 08:50:57Z tgaskins $
 */

public class WWJApplet extends JApplet
{
    protected WorldWindowGLCanvas wwd;
    protected RenderableLayer labelsLayer;
    
    /* Variables being added for the JSatTrak addition to the WorldWind Applet. 
     * Starting with: Sun and Moon bodies (update: Moon may not be used?)
     * Also adding: Time
     * Also adding: Hashtable for satellites
     */
    // Sun object
//    private Sun sun;
//    private Moon moon;
    
    //Time!
    Time currentJulianDate = new Time(); // current sim or real time (Julian Date)
    double time = 100000000000000.0; //Far too big- used to determine earliest ephermis time
    
    // date formats for displaying and reading in
    private SimpleDateFormat dateformat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss.SSS z");
    private SimpleDateFormat dateformatShort1 = new SimpleDateFormat("dd MMM y H:m:s.S z");
    private SimpleDateFormat dateformatShort2 = new SimpleDateFormat("dd MMM y H:m:s z"); // no Milliseconds
    
    // scenario epoch time settings
    private boolean epochTimeEqualsCurrentTime = false; // uses current time for scenario epoch (reset button)
    private Time scenarioEpochDate = new Time(); // scenario epoch if epochTimeisCurrentTime = false
    
    // store local time zone for printing
    TimeZone localTZ = TimeZone.getDefault();
    
    //Scenario Update variables
    int currentPlayDirection = 0; // 1= forward, -1=backward, =0-no animation step, but can update time (esentially a graphic ini or refresh)
    private double animationSimStepSeconds = 60.0; // dt in Days per animation step/time update
    
    //Animation variables
    private boolean stopHit = false;
    private long lastFPSms;
    private double fpsAnimation;
    private Timer playTimer;
    private int realTimeAnimationRefreshRateMs = 1000; // refresh rate for real time animation
    private int nonRealTimeAnimationRefreshRateMs = 50; // refresh rate for non-real time animation
    private int animationRefreshRateMs = nonRealTimeAnimationRefreshRateMs; // (current)Milliseconds
     
    //Satellites!
    // hashtable to store all the statelites currently being processed
    private Hashtable<String,AbstractSatellite> satHash = new Hashtable<String,AbstractSatellite>();

    // time dependent objects that should be update when time is updated -- NEED TO BE SAVED?
    Vector<JSatTrakTimeDependent> timeDependentObjects = new Vector<JSatTrakTimeDependent>();
    
    //Stuff from J3DEarthPanel
    ECIRenderableLayer eciLayer; // ECI layer for plotting in ECI coordinates
    ECEFRenderableLayer ecefLayer; // ECEF layer for plotting in ECEF coordinates
    EcefTimeDepRenderableLayer timeDepLayer;
    OrbitModelRenderable orbitModel; // renderable object for plotting
    ECEFModelRenderable ecefModel;
    
    private boolean viewModeECI = true; // view mode - ECI (true) or ECEF (false)
//    StarsLayer starsLayer = new StarsLayer();
    
    // view mode options
    private boolean modelViewMode = false; // default false
    private String modelViewString = ""; // to hold name of satellite to view when modelViewMode=true
    private double modelViewNearClip = 10000; // clipping pland for when in Model View mode
    private double modelViewFarClip = 5.0E7;
    private boolean smoothViewChanges = true; // for 3D view smoothing (only is set after model/earth view has been changed -needs to be fixed)
    // near/far clipping plane distances for 3d windows (can effect render speed and if full orbit is shown)
     private double farClippingPlaneDistOrbit = -1;//200000000d; // good out to geo, but slow for LEO, using AutoClipping plane view I made works better
     private double nearClippingPlaneDistOrbit = -1; // -1 value Means auto adjusting

    ViewControlsLayer viewControlsLayer;

    // sun shader
//    private RectangularNormalTessellator tessellator;
//    private LensFlareLayer lensFlareLayer;
//    private AtmosphereLayer atmosphereLayer;
//    private SunPositionProvider spp;
//    private boolean sunShadingOn = false; // controls if sun shading is used

    // ECI grid
    private ECIRadialGrid eciRadialGrid = new ECIRadialGrid();

    //Reading ephemeris data
    StkEphemerisReader Reader = new StkEphemerisReader();
    
    //Satellite input
    OnlineInput input;
    private boolean play = true;
    private boolean inputSat = true;
    private boolean end = false;
    
    //Buttons
    JButton playScenario;
    JButton pauseScenario;
    JButton resetScenario;
    JButton stepSizeUp;
    JButton stepSizeDown;
    JRadioButton ECIon;
    JRadioButton ECEFon;
    JToolBar toolbar; 
    JTextField dateDisplay;
    JTextField stepDisplay;
    StatusBar statusBar;
    JTextField statusDisplay;
    JLabel stepSizeLabel;
    JRadioButton twoDbutton;
    JRadioButton threeDbutton;
    
    //Step sizes
    double[] steps = new double[] {1, 10, 30, 60, 120, 300, 1800, 3600, 7200, 86400};
    int stepNumber = 3;
    
    BasicOrbitViewLimits limits;
    
    J2DEarthPanel twoDpanel;
    Container Content = this.getContentPane();
    private boolean twoDon = false;
    
    private boolean sunCompat = true;
    
    public WWJApplet()
    {       
    }

    @Override
    public void init()
    {
        Vector<StateVector> vector;
        try
        {
            //JOGLAppletLauncher launch = new JOGLAppletLauncher();
            // Check for initial configuration values
            String value = getParameter("InitialLatitude");
            if (value != null)
                Configuration.setValue(AVKey.INITIAL_LATITUDE, Double.parseDouble(value));
            value = getParameter("InitialLongitude");
            if (value != null)
                Configuration.setValue(AVKey.INITIAL_LONGITUDE, Double.parseDouble(value));
            value = getParameter("InitialAltitude");
            if (value != null)
                Configuration.setValue(AVKey.INITIAL_ALTITUDE, Double.parseDouble(value));
            value = getParameter("InitialHeading");
            if (value != null)
                Configuration.setValue(AVKey.INITIAL_HEADING, Double.parseDouble(value));
            value = getParameter("InitialPitch");
            if (value != null)
                Configuration.setValue(AVKey.INITIAL_PITCH, Double.parseDouble(value));

              Configuration.setValue(AVKey.TESSELLATOR_CLASS_NAME, RectangularNormalTessellator.class.getName());


            // Create World Window GL Canvas
            this.wwd = new WorldWindowGLCanvas();
            Content.add(this.wwd, BorderLayout.CENTER);
            // Create the default model as described in the current worldwind properties.
            Model m = (Model) WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME);
            this.wwd.setModel(m);
            
            //Remove original Stars Layer
//            m.getLayers().remove(0);
            
//            // Add a renderable layer for application labels
//            this.labelsLayer = new RenderableLayer();
//            this.labelsLayer.setName("Labels");
//            insertBeforeLayerName(this.wwd, this.labelsLayer, "Compass");
            
            // add EcefTimeDepRenderableLayer layer
//            timeDepLayer = new EcefTimeDepRenderableLayer(currentJulianDate.getMJD(),sun);
//            m.getLayers().add(timeDepLayer);
            //insertBeforeLayerName(this.wwd,timeDepLayer,"Labels");*/
            
            // add ECI Layer -- FOR SOME REASON IF BEFORE EFEF and turned off ECEF Orbits don't show up!! Coverage effecting this too, strange
            eciLayer = new ECIRenderableLayer(currentJulianDate.getMJD()); // create ECI layer
            orbitModel = new OrbitModelRenderable(satHash, wwd.getModel().getGlobe());
            eciLayer.addRenderable(orbitModel); // add renderable object
            eciLayer.setCurrentMJD(currentJulianDate.getMJD()); // update time again after adding renderable
            eciRadialGrid.setShowGrid(false);
            eciLayer.addRenderable(eciRadialGrid); // add grid (optional if it is on or not)
            m.getLayers().add(0,eciLayer); // add ECI Layer
            //insertBeforeLayerName(this.wwd,eciLayer, "Labels");
            
//            CountryBoundariesLayer country = new CountryBoundariesLayer();
//            country.setEnabled(false);
//            m.getLayers().add(country); 
//            Layer uhh = m.getLayers().getLayerByName("Political Boundaries");
//            m.getLayers().remove(uhh);     
            
            // add ECEF Layer
            ecefLayer = new ECEFRenderableLayer(); // create ECEF layer
            ecefModel = new ECEFModelRenderable(satHash, wwd.getModel().getGlobe());
            ecefLayer.addRenderable(ecefModel); // add renderable object
            ecefLayer.setEnabled(false);
            m.getLayers().add(ecefLayer); // add ECEF Layer
            //insertBeforeLayerName(this.wwd,ecefLayer,"Labels");
            
            RenderableLayer latLongLinesLayer = createLatLongLinesLayer();
            latLongLinesLayer.setName("Lat/Long Lines");
            latLongLinesLayer.setEnabled(false);
            m.getLayers().add(latLongLinesLayer); // add ECI Layer   
            //insertBeforeLayerName(this.wwd,latLongLinesLayer,"Labels");

            viewControlsLayer = new ViewControlsLayer();
            viewControlsLayer.setLayout(AVKey.VERTICAL); // VOTD change from LAYOUT_VERTICAL (9/june/09)
            viewControlsLayer.setScale(6/10d);
            viewControlsLayer.setPosition(AVKey.SOUTHEAST); // put it on the right side
            viewControlsLayer.setLocationOffset( new Vec4(15,35,0,0));
            viewControlsLayer.setEnabled(true); // turn off by default
            viewControlsLayer.setShowVeControls(false);
            m.getLayers().add(1,viewControlsLayer);
            //insertBeforeCompass(wwd, viewControlsLayer);
            //getLayerPanel().update(wwd);
            wwd.addSelectListener(new ViewControlsSelectListener(wwd, viewControlsLayer));

            // first call to update time to current time:
            currentJulianDate.update2CurrentTime(); //update();// = getCurrentJulianDate(); // ini time

            // just a little touch up -- remove the milliseconds from the time
            int mil = currentJulianDate.get(Time.MILLISECOND);
            currentJulianDate.add(Time.MILLISECOND,1000-mil); // remove the milliseconds (so it shows an even second)

            // set time string format
            currentJulianDate.setDateFormat(dateformat);
            scenarioEpochDate.setDateFormat(dateformat);
            
            // create Sun object
//            sun = new Sun(currentJulianDate.getMJD());
//            // create Moon object
//            moon = new Moon();
//            Moon.MoonPosition(currentJulianDate.getMJD());        
            
            //FIX FOR TRANSPARENT EARTH PROBLEM: Remove old star layer and add new star layer.
//            starsLayer.setLongitudeOffset(Angle.fromDegrees(-eciLayer.getRotateECIdeg()));
            //insertBeforeLayerName(this.wwd,starsLayer,"View Controls");
//            m.getLayers().add(0,starsLayer);
            
            // set the sun provider to the shader
//            spp = new CustomSunPositionProvider(sun);

            try
            {
            // Use normal/shading tessellator
            // sun shading needs this
            Configuration.setValue(AVKey.TESSELLATOR_CLASS_NAME, RectangularNormalTessellator.class.getName());
            
            //ALREADY HAVE AN ATMOSPHERE LAYER (Its just not working)
            // Replace sky gradient with this atmosphere layer when using sun shading
//            this.atmosphereLayer = new AtmosphereLayer();
            //m.getLayers().add(4,this.atmosphereLayer);
            //insertBeforeLayerName(this.wwd,this.atmosphereLayer,"Labels");
            
            // Add lens flare layer
//            this.lensFlareLayer = LensFlareLayer.getPresetInstance(LensFlareLayer.PRESET_BOLD);
//            m.getLayers().add(this.lensFlareLayer);
            //insertBeforeLayerName(this.wwd,this.lensFlareLayer,"Labels");
            
            // Get tessellator
//            this.tessellator = (RectangularNormalTessellator)m.getGlobe().getTessellator();
            // set default colors for shading
//            this.tessellator.setAmbientColor(new Color(0.50f, 0.50f, 0.50f));
            }
            catch(Exception windows7)
            {sunCompat = false;}
//            LayerList WWLayers = m.getLayers();
//            String TheLayers = WWLayers.toString();
//            //System.out.println(TheLayers);
            
            for (Layer layer : m.getLayers())
            {
//            if (layer instanceof TiledImageLayer)
//            {
//                ((TiledImageLayer) layer).setShowImageTileOutlines(false);
//            }
            if (layer instanceof LandsatI3)
            {
                ((TiledImageLayer) layer).setDrawBoundingVolumes(false);
                ((TiledImageLayer) layer).setEnabled(false);
            }
            if (layer instanceof CompassLayer)
            {
                ((CompassLayer) layer).setShowTilt(true);
                ((CompassLayer) layer).setEnabled(true);
            }
            if (layer instanceof PlaceNameLayer)
            {
                ((PlaceNameLayer) layer).setEnabled(false); // off
            }
            if (layer instanceof WorldMapLayer)
            {
                ((WorldMapLayer) layer).setEnabled(false); // off
            }
            if (layer instanceof USGSUrbanAreaOrtho)
            {
                ((USGSUrbanAreaOrtho) layer).setEnabled(false); // off
            }
            // save star layer
            if (layer instanceof StarsLayer)
            {
//                starsLayer = (StarsLayer) layer;
                
                // for now just enlarge radius by a factor of 10
//                starsLayer.setRadius(starsLayer.getRadius()*10.0);
            }
            if(layer instanceof CountryBoundariesLayer)
            {
                ((CountryBoundariesLayer) layer).setEnabled(false); // off by default
            }
//            if(layer instanceof AtmosphereLayer)
//            {
//                atmosphereLayer = (AtmosphereLayer) layer;
//                atmosphereLayer.setEnabled(true);
//            }
            } // for layers
            
            //Visualization Tests
//            m.setShowWireframeExterior(true);
//            m.setShowWireframeInterior(true);
            
            // Add position listener to update light direction relative to the eye
            this.wwd.addPositionListener(new PositionListener()
            {
                Vec4 eyePoint;

                @Override
                    public void moved(PositionEvent event)
                    {
                        if(eyePoint == null || eyePoint.distanceTo3(wwd.getView().getEyePoint()) > 1000)
                        {
                            update(true);
                            eyePoint = wwd.getView().getEyePoint();
                        }
                    }
            });

            setSunShadingOn(true); // enable sun shading by default
            // END Sun Shading -------------
            
//            this.twoDpanel = createNew2dWindow();
            //Content.add(twoDpanel, BorderLayout.CENTER);
            
                        //SET UP GUI
            //Add a tool bar
            toolbar = new JToolBar();
            Content.add(toolbar, BorderLayout.PAGE_START);
            
            //Add the play button
            playScenario = new JButton("Play");
            playScenario.setBounds(30,30,70,30);
            playScenario.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playButtonActionPerformed(evt);
            }}));
            toolbar.add(playScenario);
            
            //Add pause button
            pauseScenario = new JButton("Pause");
            pauseScenario.setBounds(30,30,70,30);
            pauseScenario.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }}));
            toolbar.add(pauseScenario); 
            
            //Reset button
            resetScenario = new JButton("Reset");
            resetScenario.setBounds(30,30,70,30);
            resetScenario.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
            }}));
            toolbar.add(resetScenario);
                        
            //Step size label
            stepSizeLabel = new JLabel();
            stepSizeLabel.setText("Step Size: ");
            toolbar.add(stepSizeLabel);
            
            //Increase step size
            stepSizeUp = new JButton("+");
            stepSizeUp.setBounds(30,30,70,30);
            stepSizeUp.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stepUpButtonActionPerformed(evt);
            }}));
            toolbar.add(stepSizeUp);

            //Step size display
            stepDisplay = new JTextField();
            stepDisplay.setSize(50, 10);
            stepDisplay.setText("" + animationSimStepSeconds);
            stepDisplay.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stepDisplayActionPerformed(evt);
            }}));
            toolbar.add(stepDisplay); 
            
            //Decrease step size
            stepSizeDown = new JButton("-");
            stepSizeDown.setBounds(30,30,70,30);
            stepSizeDown.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stepDownButtonActionPerformed(evt);
            }}));
            toolbar.add(stepSizeDown);
            
            //ECI Button
            ECIon = new JRadioButton("ECI");
            ECIon.setBounds(30,30,70,30);
            ECIon.setSelected(true);
            ECIon.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eciButtonActionPerformed(evt);
            }}));
            toolbar.add(ECIon);
            
            //ECEF Button
            ECEFon = new JRadioButton("ECEF");
            ECEFon.setBounds(30,30,50,30);
            ECEFon.setSelected(true);
            ECEFon.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ecefButtonActionPerformed(evt);
            }}));
            toolbar.add(ECEFon);
            //Connet the radio buttons
            ButtonGroup bg = new ButtonGroup();
            bg.add(ECIon);
            bg.add(ECEFon);    
                        
            //two D view Button
            twoDbutton = new JRadioButton("2D View");
            twoDbutton.setSelected(false);
            twoDbutton.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                twoDButtonActionPerformed(evt);
            }}));
            toolbar.add(twoDbutton);
            
            //three D view Button
            threeDbutton = new JRadioButton("3D View");
            threeDbutton.setSelected(true);
            threeDbutton.addActionListener((new java.awt.event.ActionListener() {
                @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                threeDButtonActionPerformed(evt);
            }}));
            toolbar.add(threeDbutton);
            ButtonGroup bgView = new ButtonGroup();
            bgView.add(twoDbutton);
            bgView.add(threeDbutton); 
            
            //Add a date text field
            dateDisplay = new JTextField("Date/Time");
            dateDisplay.setText( currentJulianDate.getDateTimeStr() );
            toolbar.add(dateDisplay);
            
            //Add error/status display
            statusDisplay = new JTextField("Status: ");
            statusDisplay.setText("Running");
            toolbar.add(statusDisplay);
            
            // Add the status bar
            statusBar = new StatusBar();
            this.getContentPane().add(statusBar, BorderLayout.PAGE_END);

            // Forward events to the status bar to provide the cursor position info.
            statusBar.setEventSource(this.wwd);

            // Setup a select listener for the worldmap click-and-go feature
            this.wwd.addSelectListener(new ClickAndGoSelectListener(this.wwd, WorldMapLayer.class));
            
                        //Read satellites
            try{
            input = new OnlineInput("http://localhost:8080/parameters.html");
            int n = input.getSize();
            for (int i = 0; i <n; i++)
            {
                addCustomSat(input.getSatelliteName(i));
            }
            StkEphemerisReader reader = new StkEphemerisReader();
            double tempTime;
            for (int i = 0; i <n; i++)
            {	
                    AbstractSatellite S = satHash.get(input.getSatelliteName(i));
                    S.setGroundTrackIni2False();
                    S.setPlot2DFootPrint(false);
                    S.setShow3DFootprint(false);
                    if (input.getColor(i).startsWith("b"))
                    {
                            S.setSatColor(Color.BLUE);
                    }
                    else if (input.getColor(i).startsWith("g"))
                    {
                            S.setSatColor(Color.GREEN);
                    }
                    else if (input.getColor(i).startsWith("r"))
                    {
                            S.setSatColor(Color.RED);
                    }
                    else if (input.getColor(i).startsWith("y"))
                    {
                            S.setSatColor(Color.YELLOW);
                    }
                    else if (input.getColor(i).startsWith("w"))
                    {
                            S.setSatColor(Color.WHITE);
                    }
                    else if (input.getColor(i).startsWith("p"))
                    {
                            S.setSatColor(Color.PINK);
                    }
                    else if (input.getColor(i).startsWith("o"))
                    {
                            S.setSatColor(Color.ORANGE);
                    }
                    vector = reader.readStkEphemeris(input.getEphemerisLocation(i));
                    tempTime = StkEphemerisReader.convertScenarioTimeString2JulianDate(reader.getScenarioEpoch() + " UTC");
                    if(tempTime < time)
                    {
                        time = tempTime;
                    }
                    S.setEphemeris(vector);
                    // set default 3d model and turn on the use of 3d models
//                    S.setThreeDModelPath("globalstar/Globalstar.3ds");
//                    S.setUse3dModel(true);
                    if (input.getModelCentered(i))
                    {
                           statusDisplay.setText("Can't do that yet!");
                    }
                    else
                    {
                            //dont do anything!
                    }
            }
            setTime(time);
            statusDisplay.setText("Satellites Added");
            }
            catch(Exception e)
            {statusDisplay.setText("No satellites found");
            inputSat = false;
            currentJulianDate.update2CurrentTime();}            //Read satellites
            try{
            input = new OnlineInput("http://localhost:8080/parameters.html");
            int n = input.getSize();
            for (int i = 0; i <n; i++)
            {
                addCustomSat(input.getSatelliteName(i));
            }
            StkEphemerisReader reader = new StkEphemerisReader();
            double tempTime;
            for (int i = 0; i <n; i++)
            {	
                    AbstractSatellite S = satHash.get(input.getSatelliteName(i));
                    S.setGroundTrackIni2False();
                    S.setPlot2DFootPrint(false);
                    S.setShow3DFootprint(false);
                    if (input.getColor(i).startsWith("b"))
                    {
                            S.setSatColor(Color.BLUE);
                    }
                    else if (input.getColor(i).startsWith("g"))
                    {
                            S.setSatColor(Color.GREEN);
                    }
                    else if (input.getColor(i).startsWith("r"))
                    {
                            S.setSatColor(Color.RED);
                    }
                    else if (input.getColor(i).startsWith("y"))
                    {
                            S.setSatColor(Color.YELLOW);
                    }
                    else if (input.getColor(i).startsWith("w"))
                    {
                            S.setSatColor(Color.WHITE);
                    }
                    else if (input.getColor(i).startsWith("p"))
                    {
                            S.setSatColor(Color.PINK);
                    }
                    else if (input.getColor(i).startsWith("o"))
                    {
                            S.setSatColor(Color.ORANGE);
                    }
                    vector = reader.readStkEphemeris(input.getEphemerisLocation(i));
                    tempTime = StkEphemerisReader.convertScenarioTimeString2JulianDate(reader.getScenarioEpoch() + " UTC");
                    if(tempTime < time)
                    {
                        time = tempTime;
                    }
                    S.setEphemeris(vector);
                    // set default 3d model and turn on the use of 3d models
//                    S.setThreeDModelPath("globalstar/Globalstar.3ds");
//                    S.setUse3dModel(true);
                    if (input.getModelCentered(i))
                    {
                           statusDisplay.setText("Can't do that yet!");
                    }
                    else
                    {
                            //dont do anything!
                    }
            }
            setTime(time);
            statusDisplay.setText("Satellites Added");
            }
            catch(Exception e)
            {statusDisplay.setText("No satellites found");
            inputSat = false;
            currentJulianDate.update2CurrentTime();}
            
            updateTime(); // update plots
            //System.out.print(this.getCurrentJulTime() + "\n");
            
            setupView(); // setup needed viewing specs and use of AutoClipBasicOrbitView
            
            // Call javascript appletInit()
            try
            {
                JSObject win = JSObject.getWindow(this);
                win.call("appletInit", null);
            }
            catch (Exception ignore)
            {
            }
        }
                
        catch (Throwable e)
        {
        }
    }

    @Override
    public void start()
    {
        // Call javascript appletStart()
        try
        {
            JSObject win = JSObject.getWindow(this);
            win.call("appletStart", null);
        }
        catch (Exception ignore)
        {
        }
    }

    @Override
    public void stop()
    {
        // Call javascript appletSop()
        try
        {
            JSObject win = JSObject.getWindow(this);
            win.call("appletStop", null);
        }
        catch (Exception ignore)
        {
        }

        // Shut down World Wind
        WorldWind.shutDown();
    }

    /**
     * Adds a layer to WW current layerlist, before a named layer. Target name can be a part of the layer name
     *
     * @param wwd        the <code>WorldWindow</code> reference.
     * @param layer      the layer to be added.
     * @param targetName the partial layer name to be matched - case sensitive.
     */
    public static void insertBeforeLayerName(WorldWindow wwd, Layer layer, String targetName)
    {
        // Insert the layer into the layer list just before the target layer.
        LayerList layers = wwd.getModel().getLayers();
        int targetPosition = layers.size() - 1;
        for (Layer l : layers)
        {
            if (l.getName().indexOf(targetName) != -1)
            {
                targetPosition = layers.indexOf(l);
                break;
            }
        }
        layers.add(targetPosition, layer);
    }

    // ============== Public API - Javascript ======================= //

    /**
     * Move the current view position
     *
     * @param lat the target latitude in decimal degrees
     * @param lon the target longitude in decimal degrees
     */
    public void gotoLatLon(double lat, double lon)
    {
        this.gotoLatLon(lat, lon, Double.NaN, 0, 0);
    }

    /**
     * Move the current view position, zoom, heading and pitch
     *
     * @param lat     the target latitude in decimal degrees
     * @param lon     the target longitude in decimal degrees
     * @param zoom    the target eye distance in meters
     * @param heading the target heading in decimal degrees
     * @param pitch   the target pitch in decimal degrees
     */
    public void gotoLatLon(double lat, double lon, double zoom, double heading, double pitch)
    {
        BasicOrbitView view = (BasicOrbitView) this.wwd.getView();
        if (!Double.isNaN(lat) || !Double.isNaN(lon) || !Double.isNaN(zoom))
        {
            lat = Double.isNaN(lat) ? view.getCenterPosition().getLatitude().degrees : lat;
            lon = Double.isNaN(lon) ? view.getCenterPosition().getLongitude().degrees : lon;
            zoom = Double.isNaN(zoom) ? view.getZoom() : zoom;
            heading = Double.isNaN(heading) ? view.getHeading().degrees : heading;
            pitch = Double.isNaN(pitch) ? view.getPitch().degrees : pitch;
            view.addPanToAnimator(Position.fromDegrees(lat, lon, 0),
                Angle.fromDegrees(heading), Angle.fromDegrees(pitch), zoom, true);
        }
    }

    /**
     * Set the current view heading and pitch
     *
     * @param heading the traget heading in decimal degrees
     * @param pitch   the target pitch in decimal degrees
     */
    public void setHeadingAndPitch(double heading, double pitch)
    {
        BasicOrbitView view = (BasicOrbitView) this.wwd.getView();
        if (!Double.isNaN(heading) || !Double.isNaN(pitch))
        {
            heading = Double.isNaN(heading) ? view.getHeading().degrees : heading;
            pitch = Double.isNaN(pitch) ? view.getPitch().degrees : pitch;

            view.addHeadingPitchAnimator(
                view.getHeading(), Angle.fromDegrees(heading), view.getPitch(), Angle.fromDegrees(pitch));
        }
    }

    /**
     * Set the current view zoom
     *
     * @param zoom the target eye distance in meters
     */
    public void setZoom(double zoom)
    {
        BasicOrbitView view = (BasicOrbitView) this.wwd.getView();
        if (!Double.isNaN(zoom))
        {
            view.addZoomAnimator(view.getZoom(), zoom);
        }
    }

    /**
     * Get the WorldWindowGLCanvas
     *
     * @return the current WorldWindowGLCanvas
     */
    public WorldWindowGLCanvas getWW()
    {
        return this.wwd;
    }

    /**
     * Get the current OrbitView
     *
     * @return the current OrbitView
     */
    public OrbitView getOrbitView()
    {
        if (this.wwd.getView() instanceof OrbitView)
            return (OrbitView) this.wwd.getView();
        return null;
    }

    /**
     * Get a reference to a layer with part of its name
     *
     * @param layerName part of the layer name to match.
     *
     * @return the corresponding layer or null if not found.
     */
    public Layer getLayerByName(String layerName)
    {
        for (Layer layer : wwd.getModel().getLayers())
        {
            if (layer.getName().indexOf(layerName) != -1)
                return layer;
        }
        return null;
    }

    /**
     * Add a text label at a position on the globe.
     *
     * @param text  the text to be displayed.
     * @param lat   the latitude in decimal degrees.
     * @param lon   the longitude in decimal degrees.
     * @param font  a string describing the font to be used.
     * @param color the color to be used as an hexadecimal coded string.
     */
    public void addLabel(String text, double lat, double lon, String font, String color)
    {
        GlobeAnnotation ga = new GlobeAnnotation(text, Position.fromDegrees(lat, lon, 0),
            Font.decode(font), Color.decode(color));
        ga.getAttributes().setBackgroundColor(Color.BLACK);
        ga.getAttributes().setDrawOffset(new Point(0, 0));
        ga.getAttributes().setFrameShape(AVKey.SHAPE_NONE);
        ga.getAttributes().setEffect(AVKey.TEXT_EFFECT_OUTLINE);
        ga.getAttributes().setTextAlign(AVKey.CENTER);
        this.labelsLayer.addRenderable(ga);
    }
    
    //Added JSatTrak methods/classes
    public void updateTime()
    {
        // save old time
        double prevJulDate = currentJulianDate.getJulianDate();
        
        // Get current simulation time!             
        /*if(realTimeModeCheckBox.isSelected())
        {
            // real time mode -- just use real time
            
            // Get current time in GMT
            // calculate current Juilian Date, update to current time
            currentJulianDate.update2CurrentTime(); //update();// = getCurrentJulianDate();

        }*/
        //else
//        {
//            // non-real time mode add fraction of time to current jul date
//            //currentJulianDate += currentPlayDirection*animationSimStepDays;
//            currentJulianDate.addSeconds( currentPlayDirection*animationSimStepSeconds );
//        }
        currentJulianDate.addSeconds( currentPlayDirection*animationSimStepSeconds );
        // update sun position
//        sun.setCurrentMJD(currentJulianDate.getMJD());
                
        // if time jumps by more than 91 minutes check period of sat to see if
        // ground tracks need to be updated
        double timeDiffDays = Math.abs(currentJulianDate.getJulianDate()-prevJulDate); // in days
        checkTimeDiffResetGroundTracks(timeDiffDays);        
                
        // update date box:
        dateDisplay.setText( currentJulianDate.getDateTimeStr() );//String.format("%tc",cal) );
        
        // now propogate all satellites to the current time  
        for (AbstractSatellite sat : satHash.values() )
        {
            sat.propogate2JulDate( currentJulianDate.getJulianDate() );
        } // propgate each sat 
        
        // update any other time dependant objects
        for(JSatTrakTimeDependent tdo : timeDependentObjects)
        {
            if(tdo != null)
            {
                tdo.updateTime(currentJulianDate, satHash);
            }
        }
        WWsetMJD(currentJulianDate.getMJD());        
        forceRepainting(); // repaint 2d/3d earth
        
        
    } // update time
 public void checkTimeDiffResetGroundTracks(double timeDiffDays)
    {
        if( timeDiffDays > 91.0/1440.0)
        {
            // big time jump
            for (AbstractSatellite sat : satHash.values() )
            {
                if(sat.getShowGroundTrack() && (sat.getPeriod() <= (timeDiffDays*24.0*60.0) ) )
                {
                    sat.setGroundTrackIni2False();
                    //System.out.println(sat.getName() +" - Groundtrack Initiated");
                }
            }
        }
    } // checkTimeDiffResetGroundTracks
     public void forceRepainting()
    {
        this.update(false);
        wwd.redraw();
        try
        {
        twoDpanel.repaint();
        }
        catch(Exception e)
        {System.out.println("Didn't work");}
    }// forceRepainting
     
public void addCustomSat(String name)
    {
        // if nothing given:
        if(name == null || name.equalsIgnoreCase(""))
        {
            System.out.println("returned");
            return;
        }
        
        CustomSatellite prop = new CustomSatellite(name,this.getScenarioEpochDate());
        
        satHash.put(name, prop);

        // set satellite time to current date
        prop.propogate2JulDate(this.getCurrentJulTime());
    }
    public Time getScenarioEpochDate()
    {
        return scenarioEpochDate;
    }
        public double getCurrentJulTime()
    {
        return currentJulianDate.getJulianDate();
    }
    private RenderableLayer createLatLongLinesLayer()
    {
        RenderableLayer shapeLayer = new RenderableLayer();

            // Generate meridians
            ArrayList<Position> positions = new ArrayList<Position>(3);
            double height = 30e3; // 10e3 default
            for (double lon = -180; lon < 180; lon += 10)
            {
                Angle longitude = Angle.fromDegrees(lon);
                positions.clear();
                positions.add(new Position(Angle.NEG90, longitude, height));
                positions.add(new Position(Angle.ZERO, longitude, height));
                positions.add(new Position(Angle.POS90, longitude, height));
                Polyline polyline = new Polyline(positions);
                polyline.setFollowTerrain(false);
                polyline.setNumSubsegments(30);
                
                if(lon == -180 || lon == 0)
                {
                    polyline.setColor(new Color(1f, 1f, 0f, 0.5f)); // yellow
                }
                else
                {
                    polyline.setColor(new Color(1f, 1f, 1f, 0.5f));
                }
                
                shapeLayer.addRenderable(polyline);
            }

            // Generate parallels
            for (double lat = -80; lat < 90; lat += 10)
            {
                Angle latitude = Angle.fromDegrees(lat);
                positions.clear();
                positions.add(new Position(latitude, Angle.NEG180, height));
                positions.add(new Position(latitude, Angle.ZERO, height));
                positions.add(new Position(latitude, Angle.POS180, height));
                Polyline polyline = new Polyline(positions);
                polyline.setPathType(Polyline.LINEAR);
                polyline.setFollowTerrain(false);
                polyline.setNumSubsegments(30);
                
                if(lat == 0)
                {
                    polyline.setColor(new Color(1f, 1f, 0f, 0.5f));
                }
                else
                {
                    polyline.setColor(new Color(1f, 1f, 1f, 0.5f));
                }
                
                shapeLayer.addRenderable(polyline);
            }

            return shapeLayer;
    }
// Update worldwind wun shading
    private void update(boolean redraw)
    {
//        if(sunShadingOn) //this.enableCheckBox.isSelected())
//        {
//            // Compute Sun position according to current date and time
//            LatLon sunPos = spp.getPosition();
//            Vec4 sunvar = wwd.getModel().getGlobe().computePointFromPosition(new Position(sunPos, 0)).normalize3();
//
//            Vec4 light = sunvar.getNegative3();
//            if(sunCompat)
//            {
////            this.tessellator.setLightDirection(light);
//            this.lensFlareLayer.setSunDirection(sunvar);
//            //Already an atmosphere layer!
//            atmosphereLayer.setSunDirection(sunvar);
//            
//            }
//            // Redraw if needed
//            if(redraw)
//            {
//                wwd.redraw();
//            }
//        } // if sun Shading
        
    } // update - for sun shading

    public void setSunShadingOn(boolean useSunShading)
    {
//        if(useSunShading == sunShadingOn)
//        {
//            return; // nothing to do
//        }
//
//        sunShadingOn = useSunShading;
//
//        if(sunShadingOn)
//        {
//            // enable shading - use special atmosphere
//            for(int i = 0; i < wwd.getModel().getLayers().size(); i++)
//            {
//                Layer l = wwd.getModel().getLayers().get(i);
//                if(l instanceof SkyGradientLayer)
//                {
//                    wwd.getModel().getLayers().set(i, this.atmosphereLayer);
//                }
//            }
//        }
//        else
//        {
//            // disable shading
//            // Turn off lighting
//            if(sunCompat)
//            {
////            this.tessellator.setLightDirection(null);
//            this.lensFlareLayer.setSunDirection(null);
//            this.atmosphereLayer.setSunDirection(null);
//            }
//            // use standard atmosphere
//            for(int i = 0; i < wwd.getModel().getLayers().size(); i++)
//            {
//                Layer l = wwd.getModel().getLayers().get(i);
//                if(l instanceof AtmosphereLayer)
//                {
//                    wwd.getModel().getLayers().set(i, new SkyGradientLayer());
//                }
//            }
//            
//        } // if/else shading

        this.update(true); // redraw
    } // setSunShadingOn
    private void setupView()
    {
        if(modelViewMode == false)
        { // Earth View mode
            AutoClipBasicOrbitView bov = new AutoClipBasicOrbitView();
            wwd.setView(bov);
            
            // remove the rest of the old input handler  (does this need a remove of hover listener? - maybe it is now completely removed?)
            wwd.getInputHandler().setEventSource(null);
            
            AWTInputHandler awth = new AWTInputHandler();
            awth.setEventSource(wwd);
            wwd.setInputHandler(awth);
            awth.setSmoothViewChanges(smoothViewChanges); // FALSE MAKES THE VIEW FAST!! -- MIGHT WANT TO MAKE IT GUI Chooseable
                        
            // IF EARTH VIEW -- RESET CLIPPING PLANES BACK TO NORMAL SETTINGS!!!
            wwd.getView().setNearClipDistance(this.nearClippingPlaneDistOrbit);
            wwd.getView().setFarClipDistance(this.farClippingPlaneDistOrbit);
            
            // change class for inputHandler
            Configuration.setValue(AVKey.INPUT_HANDLER_CLASS_NAME, 
                        AWTInputHandler.class.getName());

            // re-setup control layer handler
            wwd.addSelectListener(new ViewControlsSelectListener(wwd, viewControlsLayer));
            
        } // Earth View mode
        else
        { // Model View mode
            
            // TEST NEW VIEW -- TO MAKE WORK MUST TURN OFF ECI!
            this.setViewModeECI(false);

            if(!satHash.containsKey(modelViewString))
            {
                statusDisplay.setText("No Current Satellite Selected, can't switch to Model Mode: " + modelViewString);
                return;
            }

            AbstractSatellite sat = satHash.get(modelViewString);

            BasicModelView3 bmv;
            if(wwd.getView() instanceof BasicOrbitView)
            {
                bmv = new BasicModelView3(((BasicOrbitView)wwd.getView()).getOrbitViewModel(), sat);
                //bmv = new BasicModelView3(sat);
            }
            else
            {
                bmv = new BasicModelView3(((BasicModelView3)wwd.getView()).getOrbitViewModel(), sat);
            }
            
            // remove the old hover listener -- depending on this instance of the input handler class type
            if( wwd.getInputHandler() instanceof AWTInputHandler)
            {
                ((AWTInputHandler) wwd.getInputHandler()).removeHoverSelectListener();
            }
            else if( wwd.getInputHandler() instanceof BasicModelViewInputHandler3)
            {
                ((BasicModelViewInputHandler3) wwd.getInputHandler()).removeHoverSelectListener();
            }
            
            // set view
            wwd.setView(bmv);

            // remove the rest of the old input handler
            wwd.getInputHandler().setEventSource(null);
             
            // add new input handler
            BasicModelViewInputHandler3 mih = new BasicModelViewInputHandler3();
            mih.setEventSource(wwd);
            wwd.setInputHandler(mih);
            
            // view smooth?
            mih.setSmoothViewChanges(smoothViewChanges); // FALSE MAKES THE VIEW FAST!!

            // settings for great closeups!
            wwd.getView().setNearClipDistance(modelViewNearClip);
            wwd.getView().setFarClipDistance(modelViewFarClip);
            bmv.setZoom(900000);
            bmv.setPitch(Angle.fromDegrees(45));
            
            // change class for inputHandler
            Configuration.setValue(AVKey.INPUT_HANDLER_CLASS_NAME, 
                        BasicModelViewInputHandler3.class.getName());

            // re-setup control layer handler
            wwd.addSelectListener(new ViewControlsSelectListener(wwd, viewControlsLayer));
            
        } // model view mode
        
    } // setupView
    public void setViewModeECI(boolean viewModeECI)
    {
        this.viewModeECI = viewModeECI;
        
        // take care of which view mode to use
        if(viewModeECI)
        {
            // update stars
//            starsLayer.setLongitudeOffset(Angle.fromDegrees(-eciLayer.getRotateECIdeg()));
        }
        else
        {
//            starsLayer.setLongitudeOffset(Angle.fromDegrees(0.0)); // reset to normal
        }
        
    }

        public void setTime(long millisecs)
    {
        currentJulianDate.set(millisecs);
        
        // update maps ----------------
        // set animation direction = 0
        currentPlayDirection = 0;
        // update graphics
        updateTime();
    }
    
    /**
     * Set the current time of the app.
     * @param julianDate Julian Date
     */
    public void setTime(double julianDate)
    {
        GregorianCalendar gc = Time.convertJD2Calendar(julianDate);
        setTime(gc.getTimeInMillis());        
    }

    private void animateApplet(boolean b) {
        if (b)
        {
        statusDisplay.setText("Scenario Running");
        //Hard Coded play scenario
        double date = this.getCurrentJulTime();
        double currentMJDtime = date - AstroConst.JDminusMJD;
        double deltaTT2UTC = Time.deltaT(currentMJDtime); // = TT - UTC
        double maxTempTime = 0;
        //Find the very last time in the very last ephemeris
        for (int i = 1; i<=input.getSize(); i++ )
        {
            Vector<StateVector> ephemeris = satHash.get(input.getSatelliteName(i-1)).getEphemeris();
            double tempTime = ephemeris.get(ephemeris.size()-1).state[0] - deltaTT2UTC;
            if(tempTime > maxTempTime)
            {
                maxTempTime = tempTime;
            }
        }
        
        final double maxTime =  maxTempTime;      
        playTimer = new Timer(animationRefreshRateMs, new ActionListener()
                {
                @Override
                    public void actionPerformed(ActionEvent event)
                    {
                    //Ensure we're still within ephemeris time range
                    //include step size so orbit is still shown
                    //without this, the very first instant the orbit is gone will be the end point
                    double stepJulian = animationSimStepSeconds/86400;
                    if(getCurrentJulianTime() > (maxTime-stepJulian))
                        {
                            playTimer.stop();
                            play = true;
                            statusDisplay.setText("End of Scenario");
                        }
                    // take one time step in the animation
                    currentPlayDirection = 1;
                    updateTime(); // animate
                    }
                });
        playTimer.start();
    }
        else
        {
            if(play)
            {}
            else
            {playTimer.stop();
            play = true;
            end = true;
            statusDisplay.setText("End of Scenario");}
        }
    }
    

public void playButtonActionPerformed(ActionEvent e)
{
    if(play && inputSat)
    {
        animateApplet(true);
        play = false;
    }
    else if(end)
    {}
    
}
public void pauseButtonActionPerformed(ActionEvent e)
{
    if(play)
    {}
    else
    {
        animateApplet(false);
        statusDisplay.setText("Scenario Paused");
        play = true; 
    }
}
public void resetButtonActionPerformed(ActionEvent e)
{

    animateApplet(false);
    play = true;
    statusDisplay.setText("Scenario Reset");
    if(inputSat)
    {setTime(time);}
    else
    {currentJulianDate.update2CurrentTime();}
}
public void stepUpButtonActionPerformed(ActionEvent e)
{
    if(stepNumber == steps.length-1)
    {
            statusDisplay.setText("Maximum Step Size Reached");
    }
    else
    {
    animationSimStepSeconds = steps[stepNumber+1];
    stepNumber = stepNumber+1;
    stepDisplay.setText("" +animationSimStepSeconds);
    statusDisplay.setText("Step Size Increased");
    }
}
public void stepDownButtonActionPerformed(ActionEvent e)
{
    if(stepNumber>0)
    {
    animationSimStepSeconds = steps[stepNumber-1];
    stepNumber = stepNumber-1;
    stepDisplay.setText("" + animationSimStepSeconds);
    statusDisplay.setText("Step Size Decreased");
    }
    else
    {
        statusDisplay.setText("Minimum Step Size Reached");
    }
}
public void eciButtonActionPerformed(ActionEvent e)
{
    viewModeECI = true;
    statusDisplay.setText("Earth Centered Inertial View");
}
public void ecefButtonActionPerformed(ActionEvent e)
{
    viewModeECI = false;
    statusDisplay.setText("Earth Centered Earth Fixed View");
}
public void twoDButtonActionPerformed(ActionEvent e)
{
    if(twoDon)
    {}
    else
    {
    try
    {
        Content.remove(wwd);
    }
    catch(Exception nopanel)
    {}
    Content.add(twoDpanel, BorderLayout.CENTER);
    this.setSize(this.getWidth()-1, this.getHeight());
    this.setSize(this.getWidth()+1, this.getHeight());
    twoDon = true;
    statusDisplay.setText("2D View");
    }
}
public void threeDButtonActionPerformed(ActionEvent e)
{
    if(twoDon)
    {
    try
    {
        Content.remove(twoDpanel);
    }
    catch(Exception nopanel)
    {}
    Content.add(wwd, BorderLayout.CENTER);
    twoDon = false;
    statusDisplay.setText("3D View");
    }
}
public void stepDisplayActionPerformed(ActionEvent e)
{
    //Assume it worked
    boolean successStep = true;
    double tempStep = animationSimStepSeconds;
    try
    {
        String text = stepDisplay.getText();
        tempStep = Double.parseDouble(text); 
    }
    catch(Exception oops)
    {
        successStep = false;
        statusDisplay.setText("Improper step size");
    }
    if(successStep)
    {
        animationSimStepSeconds = tempStep;
        statusDisplay.setText("Step Size Changed");
    }
}

public double getCurrentJulianTime()
{
    return this.getCurrentJulTime();
}
public void WWsetMJD(double mjd)
    {
               
        if(viewModeECI)
        {
//            // Hmm need to do something to keep the ECI view moving even after user interaction
//            // seems to work after you click off globe after messing with it
//            // this fixes the problem:
            //NEED TO GET THE STOP STATE ITERATOR WORKING!
//              wwd.getView().stopStateIterators();
            wwd.getView().stopMovement(); //seems to fix prop in v0.5 
//            // update rotation of view and Stars
            double theta0 = eciLayer.getRotateECIdeg();
//
            // UPDATE TIME
            eciLayer.setCurrentMJD(mjd);
//
            double thetaf = eciLayer.getRotateECIdeg(); // degrees
//
//            // move view
//
//            //Quaternion q0 = ((BasicOrbitView) wwd.getView()).getRotation();
//            //Vec4 vec = ((BasicOrbitView) wwd.getView()).getEyePoint();
//            
//            //Position pos = ((BasicOrbitView) wwd.getView()).getCurrentEyePosition();
            Position pos = ((BasicOrbitView) wwd.getView()).getCenterPosition(); // WORKS
//            
//            // amount to rotate the globe (degrees) around poles axis
            double rotateEarthDelta = thetaf - theta0; // deg
//
//            //Quaternion q = Quaternion.fromRotationYPR(Angle.fromDegrees(0), Angle.fromDegrees(rotateEarthDelta), Angle.fromDegrees(0.0));
//            // rotate the earth around z axis by rotateEarthDelta
//            //double[][] rz = MathUtils.R_z(rotateEarthDelta*Math.PI/180);
//            //double[] newEyePos = MathUtils.mult(rz, new double[] {vec.x,vec.y,vec.z});
////            Angle newLon = pos.getLongitude().addDegrees(-rotateEarthDelta);
////            Position newPos = new Position(pos.getLatitude(),newLon,pos.getElevation());
//            
//            //Position newPos = pos.add(new Position(Angle.fromDegrees(0),Angle.fromDegrees(-rotateEarthDelta),0.0));
            Position newPos = pos.add(new Position(Angle.fromDegrees(0),Angle.fromDegrees(-rotateEarthDelta),0.0)); // WORKS
//            
//            // rotation in 3D space is "added" to the quaternion by quaternion multiplication
////            try // try around it to prevent problems when running the simulation and then opening a new 3D window (this is called before the wwj is initalized)
////            {
//                //((BasicOrbitView) wwd.getView()).setRotation(q0.multiply(q));
//            // BUG -- ALWATS REORIENTS VIEW TO NORTH UP AND NO TILT!  -- fixed 15  Jul 2008 SEG
//                //((BasicOrbitView) wwd.getView()).setEyePosition(newPos);
               ((BasicOrbitView) wwd.getView()).setCenterPosition(newPos); // WORKS  -- fixed 15  Jul 2008 SEG
////            }
////            catch(Exception e)
////            {
////                // do nothing, it will catch up next update
////            }
//
            // star layer
//            starsLayer.setLongitudeOffset(Angle.fromDegrees(-eciLayer.getRotateECIdeg()));
//            
        } // if ECI
        else
        {
            // EFEC - just update time
            eciLayer.setCurrentMJD(mjd);
            
            // star layer
//            starsLayer.setLongitudeOffset(Angle.fromDegrees(-eciLayer.getRotateECIdeg()));
        }
        
        // debug - reset view to follow sat
        //setViewCenter(15000000); // set this only if user has picked a satellite to follow!

        // update layer that needs time updates
        timeDepLayer.setCurrentMJD(mjd);
        
    } // set MJD

// public J2DEarthPanel createNew2dWindow()
//    {
//        
//        // create 2D Earth Panel:
//        //J2DEarthPanel newPanel = new J2DEarthPanel(satHash);
////        J2DEarthPanel newPanel = new J2DEarthPanel(satHash, currentJulianDate, sun);
//
//        String windowName = "2D Earth Window";
////        newPanel.setName(windowName);
////        return newPanel;
//    }
}
