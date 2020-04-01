/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.ui.hopgui.file.pipeline.delegates;

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiToolbarElement;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.engine.EngineMetrics;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IEngineMetric;
import org.apache.hop.pipeline.step.StepInterface;
import org.apache.hop.pipeline.step.StepStatus;
import org.apache.hop.ui.core.PropsUI;
import org.apache.hop.ui.core.gui.GUIResource;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

@GuiPlugin
public class HopGuiPipelineGridDelegate {
  private static Class<?> PKG = HopGui.class; // for i18n purposes, needed by Translator!!

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiJobGridDelegate-ToolBar";
  public static final String TOOLBAR_ICON_SHOW_HIDE_INACTIVE = "ToolbarIcon-10000-ShowHideInactive";
  public static final String TOOLBAR_ICON_SHOW_HIDE_SELECTED = "ToolbarIcon-10010-ShowHideSelected";

  public static final long UPDATE_TIME_VIEW = 2000L;

  private HopGui hopUi;
  private HopGuiPipelineGraph pipelineGraph;

  private CTabItem pipelineGridTab;

  private TableView pipelineGridView;

  private ToolBar toolbar;
  private GuiCompositeWidgets toolbarWidget;

  private Composite pipelineGridComposite;

  private boolean hideInactiveSteps;

  private boolean showSelectedSteps;

  private final ReentrantLock refreshViewLock;

  /**
   * @param hopUi
   * @param pipelineGraph
   */
  public HopGuiPipelineGridDelegate( HopGui hopUi, HopGuiPipelineGraph pipelineGraph ) {
    this.hopUi = hopUi;
    this.pipelineGraph = pipelineGraph;
    this.refreshViewLock = new ReentrantLock();
    hideInactiveSteps = false;
  }

  public void showGridView() {

    if ( pipelineGridTab == null || pipelineGridTab.isDisposed() ) {
      addPipelineGrid();
    } else {
      pipelineGridTab.dispose();

      pipelineGraph.checkEmptyExtraView();
    }
  }

  /**
   * Add a grid with the execution metrics per step in a table view
   */
  public void addPipelineGrid() {

    // First, see if we need to add the extra view...
    //
    if ( pipelineGraph.extraViewComposite == null || pipelineGraph.extraViewComposite.isDisposed() ) {
      pipelineGraph.addExtraView();
    } else {
      if ( pipelineGridTab != null && !pipelineGridTab.isDisposed() ) {
        // just set this one active and get out...
        //
        pipelineGraph.extraViewTabFolder.setSelection( pipelineGridTab );
        return;
      }
    }

    pipelineGridTab = new CTabItem( pipelineGraph.extraViewTabFolder, SWT.NONE );
    pipelineGridTab.setImage( GUIResource.getInstance().getImageShowGrid() );
    pipelineGridTab.setText( BaseMessages.getString( PKG, "HopGui.PipelineGraph.GridTab.Name" ) );

    pipelineGridComposite = new Composite( pipelineGraph.extraViewTabFolder, SWT.NONE );
    pipelineGridComposite.setLayout( new FormLayout() );

    addToolBar();

    //ignore whitespace for stepname column valueMeta, causing sorting to ignore whitespace
    String stepNameColumnName = BaseMessages.getString( PKG, "PipelineLog.Column.Stepname" );
    ValueMetaInterface valueMeta = new ValueMetaString( stepNameColumnName );
    valueMeta.setIgnoreWhitespace( true );
    ColumnInfo stepNameColumnInfo =
      new ColumnInfo( stepNameColumnName, ColumnInfo.COLUMN_TYPE_TEXT, false,
        true );
    stepNameColumnInfo.setValueMeta( valueMeta );

    ColumnInfo[] colinf =
      new ColumnInfo[] {
        stepNameColumnInfo,
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Copynr" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Read" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Written" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Input" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Output" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Updated" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Rejected" ), ColumnInfo.COLUMN_TYPE_TEXT, false,
          true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Errors" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Active" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Time" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.Speed" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ),
        new ColumnInfo(
          BaseMessages.getString( PKG, "PipelineLog.Column.PriorityBufferSizes" ), ColumnInfo.COLUMN_TYPE_TEXT,
          false, true ), };

    colinf[ 1 ].setAllignement( SWT.RIGHT );
    colinf[ 2 ].setAllignement( SWT.RIGHT );
    colinf[ 3 ].setAllignement( SWT.RIGHT );
    colinf[ 4 ].setAllignement( SWT.RIGHT );
    colinf[ 5 ].setAllignement( SWT.RIGHT );
    colinf[ 6 ].setAllignement( SWT.RIGHT );
    colinf[ 7 ].setAllignement( SWT.RIGHT );
    colinf[ 8 ].setAllignement( SWT.RIGHT );
    colinf[ 9 ].setAllignement( SWT.LEFT );
    colinf[ 10 ].setAllignement( SWT.RIGHT );
    colinf[ 11 ].setAllignement( SWT.RIGHT );
    colinf[ 12 ].setAllignement( SWT.RIGHT );

    pipelineGridView = new TableView( pipelineGraph.getManagedObject(), pipelineGridComposite, SWT.BORDER
      | SWT.FULL_SELECTION | SWT.MULTI, colinf, 1,
      true, // readonly!
      null, // Listener
      hopUi.getProps() );
    FormData fdView = new FormData();
    fdView.left = new FormAttachment( 0, 0 );
    fdView.right = new FormAttachment( 100, 0 );
    fdView.top = new FormAttachment( toolbar, 0 );
    fdView.bottom = new FormAttachment( 100, 0 );
    pipelineGridView.setLayoutData( fdView );

    ColumnInfo numberColumn = pipelineGridView.getNumberColumn();
    ValueMetaInterface numberColumnValueMeta = new ValueMetaString( "#", HopGuiPipelineGridDelegate::subStepCompare );
    numberColumn.setValueMeta( numberColumnValueMeta );

    // Timer updates the view every UPDATE_TIME_VIEW interval
    final Timer tim = new Timer( "HopGuiPipelineGraph: " + pipelineGraph.getMeta().getName() );

    TimerTask timtask = new TimerTask() {
      public void run() {
        if ( !hopUi.getDisplay().isDisposed() ) {
          hopUi.getDisplay().asyncExec( HopGuiPipelineGridDelegate.this::refreshView );
        }
      }
    };

    tim.schedule( timtask, 0L, UPDATE_TIME_VIEW );

    pipelineGridTab.addDisposeListener( new DisposeListener() {
      public void widgetDisposed( DisposeEvent disposeEvent ) {
        tim.cancel();
      }
    } );

    pipelineGridTab.setControl( pipelineGridComposite );

    pipelineGraph.extraViewTabFolder.setSelection( pipelineGridTab );
  }

  private void addToolBar() {

    toolbar = new ToolBar( pipelineGridComposite, SWT.BORDER | SWT.WRAP | SWT.SHADOW_OUT | SWT.LEFT | SWT.HORIZONTAL );
    FormData fdToolBar = new FormData();
    fdToolBar.left = new FormAttachment( 0, 0 );
    fdToolBar.top = new FormAttachment( 0, 0 );
    fdToolBar.right = new FormAttachment( 100, 0 );
    toolbar.setLayoutData( fdToolBar );
    hopUi.getProps().setLook( toolbar, Props.WIDGET_STYLE_TOOLBAR );

    toolbarWidget = new GuiCompositeWidgets( hopUi.getVariableSpace() );
    toolbarWidget.createCompositeWidgets( this, null, toolbar, GUI_PLUGIN_TOOLBAR_PARENT_ID, null );
    toolbar.pack();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ICON_SHOW_HIDE_INACTIVE,
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "PipelineLog.Button.ShowOnlyActiveSteps",
    toolTip = "PipelineLog.Button.ShowOnlyActiveSteps",
    i18nPackageClass = HopGui.class,
    image = "ui/images/show-inactive.svg"
  )
  public void showHideInactive() {
    hideInactiveSteps = !hideInactiveSteps;

    ToolItem toolItem = toolbarWidget.findToolItem( TOOLBAR_ICON_SHOW_HIDE_INACTIVE );
    if ( toolItem != null ) {
      if ( hideInactiveSteps ) {
        toolItem.setImage( GUIResource.getInstance().getImageHideInactive() );
      } else {
        toolItem.setImage( GUIResource.getInstance().getImageShowInactive() );
      }
    }
    refreshView();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ICON_SHOW_HIDE_SELECTED,
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "PipelineLog.Button.ShowOnlySelectedSteps",
    toolTip = "PipelineLog.Button.ShowOnlySelectedSteps",
    i18nPackageClass = HopGui.class,
    image = "ui/images/toolbar/show-all.svg"
  )
  public void showHideSelected() {
    showSelectedSteps = !showSelectedSteps;

    ToolItem toolItem = toolbarWidget.findToolItem( TOOLBAR_ICON_SHOW_HIDE_SELECTED );
    if ( toolItem != null ) {
      if ( showSelectedSteps ) {
        toolItem.setImage( GUIResource.getInstance().getImageShowSelected() );
      } else {
        toolItem.setImage( GUIResource.getInstance().getImageShowAll() );
      }
    }
    refreshView();
  }

  private void refreshView() {
    refreshViewLock.lock();
    try {
      if ( pipelineGraph.pipeline == null || pipelineGridView == null || pipelineGridView.isDisposed() ) {
        return;
      }

      // Get the metrics from the engine
      //
      EngineMetrics engineMetrics = pipelineGraph.pipeline.getEngineMetrics();
      List<IEngineComponent> shownComponents = new ArrayList<>();
      for ( IEngineComponent component : engineMetrics.getComponents() ) {
        boolean select = true;
        // If we hide inactive components we only want to see stuff running
        //
        select = select && ( !hideInactiveSteps || component.isRunning() );

        // If we opted to only see selected components...
        //
        select = select && ( !showSelectedSteps || component.isSelected() );

        if ( select ) {
          shownComponents.add( component );
        }
      }

      // Build a list of columns to show...
      //
      List<ColumnInfo> columns = new ArrayList<>();

      // First the name of the component (step):
      // Then the copy number
      // TODO: rename step to component
      //
      columns.add( new ColumnInfo( BaseMessages.getString( PKG, "PipelineLog.Column.Stepname" ), ColumnInfo.COLUMN_TYPE_TEXT, false, true ) );
      ColumnInfo copyColumn = new ColumnInfo( BaseMessages.getString( PKG, "PipelineLog.Column.Copynr" ), ColumnInfo.COLUMN_TYPE_TEXT, true, true );
      copyColumn.setAllignement( SWT.RIGHT );
      columns.add( copyColumn );

      List<IEngineMetric> usedMetrics = new ArrayList( engineMetrics.getMetricsList() );
      Collections.sort( usedMetrics, new Comparator<IEngineMetric>() {
        @Override public int compare( IEngineMetric o1, IEngineMetric o2 ) {
          return o1.getDisplayPriority().compareTo( o2.getDisplayPriority() );
        }
      } );

      for ( IEngineMetric metric : usedMetrics ) {
        ColumnInfo column = new ColumnInfo( metric.getHeader(), ColumnInfo.COLUMN_TYPE_TEXT, metric.isNumeric(), true );
        column.setToolTip( metric.getTooltip() );
        ValueMetaInterface stringMeta = new ValueMetaString( metric.getCode() );
        ValueMetaInteger valueMeta = new ValueMetaInteger( metric.getCode(), 15, 0 );
        valueMeta.setConversionMask( " #" );
        stringMeta.setConversionMetadata( valueMeta );
        column.setValueMeta( stringMeta );
        column.setAllignement( SWT.RIGHT );
        columns.add( column );
      }

      // Also add the status and speed
      //
      ValueMetaInterface stringMeta = new ValueMetaString( "speed" );
      ValueMetaInteger speedMeta = new ValueMetaInteger( "speed", 15, 0 );
      speedMeta.setConversionMask( " ###,###,###,##0" );
      stringMeta.setConversionMetadata( speedMeta );
      ColumnInfo speedColumn = new ColumnInfo( "Speed", ColumnInfo.COLUMN_TYPE_TEXT, false, true ); // TODO i18n
      speedColumn.setValueMeta( stringMeta );
      speedColumn.setAllignement( SWT.RIGHT );
      columns.add( speedColumn );

      columns.add( new ColumnInfo( "Status", ColumnInfo.COLUMN_TYPE_TEXT, false, true ) ); // TODO i18n

      // Remove the old stuff on the composite...
      //
      pipelineGridView.dispose();
      pipelineGridView = new TableView( pipelineGraph.getManagedObject(), pipelineGridComposite, SWT.NONE, columns.toArray( new ColumnInfo[ 0 ] ), shownComponents.size(), null, PropsUI.getInstance() );
      pipelineGridView.setSortable( false ); // TODO: re-implement
      FormData fdView = new FormData();
      fdView.left = new FormAttachment( 0, 0 );
      fdView.right = new FormAttachment( 100, 0 );
      fdView.top = new FormAttachment( toolbar, 0 );
      fdView.bottom = new FormAttachment( 100, 0 );
      pipelineGridView.setLayoutData( fdView );

      // Fill the grid...
      //
      int row = 0;
      for ( IEngineComponent component : shownComponents ) {
        int col = 0;

        TableItem item = pipelineGridView.table.getItem( row++ );
        item.setText( col++, Integer.toString( row ) );
        item.setText( col++, Const.NVL( component.getName(), "" ) );
        item.setText( col++, Integer.toString( component.getCopyNr() ) );

        for ( IEngineMetric metric : usedMetrics ) {
          Long value = engineMetrics.getComponentMetric( component, metric );
          item.setText( col++, value == null ? "" : Long.toString( value ) );
        }
        String speed = engineMetrics.getComponentSpeedMap().get( component );
        item.setText( col++, Const.NVL( speed, "" ) );
        String status = engineMetrics.getComponentStatusMap().get( component );
        item.setText( col++, Const.NVL( status, "" ) );
      }
      pipelineGridView.optWidth( true );
      pipelineGridComposite.layout( true, true );
    } finally {
      refreshViewLock.unlock();
    }
  }

  private void updateRowFromBaseStep( StepInterface baseStep, TableItem row ) {
    StepStatus stepStatus = new StepStatus( baseStep );

    String[] fields = stepStatus.getPipelineLogFields();

    updateCellsIfChanged( fields, row );

    // Error lines should appear in red:
    if ( baseStep.getErrors() > 0 ) {
      row.setBackground( GUIResource.getInstance().getColorRed() );
    } else {
      row.setBackground( GUIResource.getInstance().getColorWhite() );
    }
  }

  /**
   * Anti-flicker: if nothing has changed, don't change it on the screen!
   *
   * @param fields
   * @param row
   */
  private void updateCellsIfChanged( String[] fields, TableItem row ) {
    for ( int f = 1; f < fields.length; f++ ) {
      if ( !fields[ f ].equalsIgnoreCase( row.getText( f ) ) ) {
        row.setText( f, fields[ f ] );
      }
    }
  }

  public CTabItem getPipelineGridTab() {
    return pipelineGridTab;
  }

  /**
   * Sub Step Compare
   * <p>
   * Note - nulls must be handled outside of this method
   *
   * @param o1 - First object to compare
   * @param o2 - Second object to compare
   * @return 0 if equal, integer greater than 0 if o1 > o2, integer less than 0 if o2 > o1
   */
  static int subStepCompare( Object o1, Object o2 ) {
    final String[] string1 = o1.toString().split( "\\." );
    final String[] string2 = o2.toString().split( "\\." );

    //Compare the base step first
    int cmp = Integer.compare( Integer.parseInt( string1[ 0 ] ), Integer.parseInt( string2[ 0 ] ) );

    //if the base step numbers are equal, then we need to compare the sub step numbers
    if ( cmp == 0 ) {
      if ( string1.length == 2 && string2.length == 2 ) {
        //compare the sub step numbers
        cmp = Integer.compare( Integer.parseInt( string1[ 1 ] ), Integer.parseInt( string2[ 1 ] ) );
      } else if ( string1.length < string2.length ) {
        cmp = -1;
      } else if ( string2.length < string1.length ) {
        cmp = 1;
      }
    }
    return cmp;
  }
}