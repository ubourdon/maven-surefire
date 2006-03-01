package org.apache.maven.surefire;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.surefire.battery.Battery;
import org.apache.maven.surefire.battery.TestNGBattery;
import org.apache.maven.surefire.battery.assertion.BatteryTestFailedException;
import org.apache.maven.surefire.report.ReportEntry;
import org.apache.maven.surefire.report.Reporter;
import org.apache.maven.surefire.report.ReporterManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author Jason van Zyl
 * @version $Id$
 */
public class Surefire
{
    private static ResourceBundle resources = ResourceBundle.getBundle( "org.apache.maven.surefire.surefire" );

    private List batteryHolders;

    private List reports;

    private ReporterManager reporterManager;

    private ClassLoader classLoader;

    private String reportsDirectory;

    private String testSourceDirectory;

    private boolean forceTestNG;

    private String groups;

    private String excludedGroups;

    private int threadCount;

    private boolean parallel;

    public boolean run( List reports, List batteryHolders, String reportsDirectory )
        throws Exception
    {
        ClassLoader classLoader = this.getClass().getClassLoader();

        return run( reports, batteryHolders, classLoader, reportsDirectory );
    }

    public boolean run( List reports, List batteryHolders, ClassLoader classLoader, String reportsDirectory )
        throws Exception
    {
        return run( reports, batteryHolders, classLoader, reportsDirectory, Boolean.FALSE, null, null, new Integer( 0 ),
                    Boolean.FALSE, null );
    }

    public boolean run( List reports, List batteryHolders, ClassLoader classLoader, String reportsDirectory,
                        Boolean forceTestNG, String groups, String excludedGroups, Integer threadCount,
                        Boolean parallel, String testSourceDirectory )
        throws Exception
    {
        if ( reports == null || batteryHolders == null || classLoader == null )
        {
            throw new NullPointerException();
        }

        if ( batteryHolders.size() == 0 )
        {
            throw new IllegalArgumentException();
        }

        this.batteryHolders = batteryHolders;

        this.reports = reports;

        this.classLoader = classLoader;

        this.reportsDirectory = reportsDirectory;

        this.forceTestNG = forceTestNG.booleanValue();

        this.groups = groups;

        this.excludedGroups = excludedGroups;

        this.threadCount = threadCount.intValue();

        this.parallel = parallel.booleanValue();

        this.testSourceDirectory = testSourceDirectory;

        return run();
    }

    public boolean run()
        throws Exception
    {
/* TODO
        //required for jdk14 javadoc annotations
        if ( testSourceDirectory != null )
        {
            SurefireUtils.setTestSourceDirectory( testSourceDirectory );
        }
*/

        List batts = instantiateBatteries( batteryHolders, classLoader );

        reporterManager = new ReporterManager( instantiateReports( reports, classLoader ), reportsDirectory );

/* TODO: new, remove
        boolean jvm15 = false;
        if ( System.getProperty( "java.version" ).indexOf( "1.5" ) > -1 )
        {
            jvm15 = true;
        }
*/

        try
        {
            reporterManager.runStarting( 100 );

            if ( batts.size() > 0 )
            {
                int nbTests = 0;

                for ( Iterator i = batts.iterator(); i.hasNext(); )
                {
                    Battery battery = (Battery) i.next();

                    int testCount = 0;

                    try
                    {
                        testCount = battery.getTestCount();
                    }
                    catch ( BatteryTestFailedException e )
                    {
                        e.printStackTrace();

                        ReportEntry report = new ReportEntry( e, "org.apache.maven.surefire.Runner",
                                                              getResourceString( "bigProblems" ), e );

                        reporterManager.batteryAborted( report );
                    }

                    //TestNG needs a little config love -- TODO
/*                    if ( battery instanceof TestNGXMLBattery )
                    {
                        TestNGXMLBattery xbat = (TestNGXMLBattery) battery;
                        xbat.setOutputDirectory( reportsDirectory );
                        xbat.setReporter( new TestNGReporter( reporterManager, this ) );
                        xbat.execute( reporterManager );
                        nbTests += xbat.getTestCount();
                    }
                    else */if ( testCount > 0 )
                {
                    executeBattery( battery, reporterManager );

                    nbTests += testCount;
                }

                    List list = new ArrayList();

                    for ( Iterator j = battery.getSubBatteryClassNames()
                        .iterator(); j.hasNext(); )
                    {
                        String s = (String) j.next();

                        list.add( new Object[]{s, null} );
                    }

                    List subBatteries = instantiateBatteries( list, classLoader );

/* TODO
                    //Handle testng tests
                    if ( forceTestNG || isTestNG( subBatteries ) )
                    {
                        TestNG testNG = new TestNG();
                        List classes = new ArrayList();
                        for ( Iterator j = subBatteries.iterator(); j.hasNext(); )
                        {
                            Battery b = (Battery) j.next();
                            if ( b instanceof TestNGBattery )
                            {
                                TestNGBattery tb = (TestNGBattery) b;
                                classes.add( tb.getTestClass() );
                                j.remove();
                            }
                            else if ( forceTestNG && b instanceof JUnitBattery )
                            {
                                JUnitBattery jb = (JUnitBattery) b;
                                classes.add( jb.getTestClass() );
                                j.remove();
                            }
                        }

                        //configure testng parameters
                        ClassSuite classSuite = new ClassSuite( groups != null ? groups : "TestNG Suite",
                                                                Utils.classesToXmlClasses( (Class[]) classes.toArray(
                                                                    new Class[classes.size()] ) ) );
                        testNG.setCommandLineSuite( classSuite );
                        testNG.setOutputDirectory( reportsDirectory );
                        TestNGReporter testngReporter = new TestNGReporter( reporterManager, this );
                        testNG.addListener( (ITestListener) testngReporter );
                        testNG.addListener( (ISuiteListener) testngReporter );
                        // TODO: bring back when TestNG returns the method
//                        testNG.setReportResults(false);
                        testNG.setThreadCount( threadCount );
                        testNG.setParallel( parallel );

                        if ( groups != null )
                        {
                            testNG.setGroups( groups );
                        }
                        if ( excludedGroups != null )
                        {
                            testNG.setExcludedGroups( excludedGroups );
                        }

                        //set source path so testng can find javadoc
                        //annotations if not in 1.5 jvm
                        if ( !jvm15 && testSourceDirectory != null )
                        {
                            testNG.setSourcePath( testSourceDirectory );
                        }

                        //actually runs all the tests
                        List result = testNG.runSuitesLocally();
                        nbTests += result.size();
                    }
*/

                    //continue normal mode
                    for ( Iterator j = subBatteries.iterator(); j.hasNext(); )
                    {
                        Battery b = (Battery) j.next();

                        testCount = 0;

                        try
                        {
                            testCount = b.getTestCount();
                        }
                        catch ( BatteryTestFailedException e )
                        {
                            e.printStackTrace();

                            ReportEntry report = new ReportEntry( e, "org.apache.maven.surefire.SurefireRunner",
                                                                  getResourceString( "bigProblems" ), e );

                            reporterManager.batteryAborted( report );
                        }

                        if ( testCount > 0 )
                        {

                            executeBattery( b, reporterManager );

                            nbTests += testCount;
                        }
                    }

                }

                if ( nbTests == 0 )
                {
                    reporterManager.writeMessage( "There are no tests to run." );
                }
            }
            else
            {
                reporterManager.writeMessage( "There are no batteries to run." );
            }

            reporterManager.runCompleted();
        }
        catch ( Throwable ex )
        {
            ex.printStackTrace();

            ReportEntry report =
                new ReportEntry( ex, "org.apache.maven.surefire.Runner", getResourceString( "bigProblems" ), ex );

            reporterManager.runAborted( report );
        }

        reporterManager.resume();

        return !( reporterManager.getNbErrors() > 0 || reporterManager
            .getNbFailures() > 0 );
    }

    /**
     * Determines if <i>any</i> of the batteries specified
     * is an instance of {@link TestNGBattery}.
     *
     * @param batteries The batteries to check
     * @return True, if any of the objects are an instanceof {@link TestNGBattery}
     */
    public boolean isTestNG( List batteries )
    {
        if ( batteries.size() > 0 )
        {
            for ( int i = 0; i < batteries.size(); i++ )
            {
                Object obj = batteries.get( i );
                if ( obj instanceof TestNGBattery )
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @param battery
     * @param reportManager
     * @throws Exception
     */
    public void executeBattery( Battery battery, ReporterManager reportManager )
        throws Exception
    {
        try
        {
            String rawString = getResourceString( "suiteExecutionStarting" );

            ReportEntry report = new ReportEntry( this, battery.getBatteryName(), battery.getBatteryName(), rawString );

            reportManager.batteryStarting( report );

            try
            {
                battery.execute( reportManager );

                rawString = getResourceString( "suiteCompletedNormally" );

                report = new ReportEntry( this, battery.getBatteryName(), battery.getBatteryName(), rawString );

                reportManager.batteryCompleted( report );
            }
            catch ( RuntimeException e )
            {
                e.printStackTrace();

                rawString = getResourceString( "executeException" );

                report = new ReportEntry( this, battery.getBatteryName(), battery.getBatteryName(), rawString, e );

                reportManager.batteryAborted( report );
            }

            reportManager.runCompleted();

            reportManager.dispose();
        }
        catch ( Throwable ex )
        {
            ReportEntry report =
                new ReportEntry( ex, "org.apache.maven.surefire.Runner", getResourceString( "bigProblems" ), ex );

            reportManager.runAborted( report );
        }
    }

    /**
     * @param batteryHolders
     * @param loader
     * @return
     * @throws Exception
     */
    public static List instantiateBatteries( List batteryHolders, ClassLoader loader )
        throws Exception
    {
        List batteries = new ArrayList();

        for ( int i = 0; i < batteryHolders.size(); i++ )
        {
            Object[] holder = (Object[]) batteryHolders.get( i );

            Object battery = SurefireUtils.instantiateBattery( holder, loader );

            if ( battery != null )
            {
                batteries.add( battery );
            }
        }

        return batteries;
    }

    protected List instantiateReports( List reportClassNames, ClassLoader classLoader )
        throws Exception
    {
        List reports = new ArrayList();

        boolean fail = false;

        ClassLoader reporterClassLoader = Reporter.class.getClassLoader();

        for ( Iterator i = reportClassNames.iterator(); i.hasNext(); )
        {
            String reportClassName = (String) i.next();

            try
            {
                Class reportClass = reporterClassLoader
                    .loadClass( reportClassName );

                // assert Reporter.class.isAssignableFrom(reportClass);

                Reporter report = (Reporter) reportClass.newInstance();

                report.setReportsDirectory( reportsDirectory );

                reports.add( report );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }

        if ( fail )
        {
            throw new RuntimeException( "couldn't assign reports as expected" );
        }

        return reports;
    }

    public static String getResourceString( String key )
    {
        return resources.getString( key );
    }
}
