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

package org.apache.hop.pipeline.transforms.csvinput;

import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.vfs.HopVFS;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;
import org.apache.hop.resource.ResourceDefinition;
import org.apache.hop.resource.ResourceEntry;
import org.apache.hop.resource.ResourceEntry.ResourceType;
import org.apache.hop.resource.ResourceNamingInterface;
import org.apache.hop.resource.ResourceReference;
import org.apache.hop.pipeline.transforms.common.CsvInputAwareMeta;
import org.apache.hop.core.file.InputFileMetaInterface;
import org.apache.hop.core.file.TextFileInputField;
import org.apache.hop.pipeline.transforms.fileinput.TextFileInputMeta;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author matt
 * @version 3.0
 * @since 2007-07-05
 */

@Transform(
        id = "CSVInput",
        image = "ui/images/TFI.svg",
        i18nPackageName = "org.apache.hop.pipeline.transforms.csvinput",
        name = "BaseTransform.TypeLongDesc.CsvInput",
        description = "BaseTransform.TypeTooltipDesc.CsvInput",
        categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input"
)
public class CsvInputMeta
  extends BaseTransformMeta
  implements TransformMetaInterface<CsvInput, CsvInputData>,
             InputFileMetaInterface<CsvInput, CsvInputData>, CsvInputAwareMeta {

  public static final String TRANSFORM_ATTRIBUTES_FILE = "/transform-attributes.xml";

  private static Class<?> PKG = CsvInput.class; // for i18n purposes, needed by Translator!!

  private String filename;

  private String filenameField;

  private boolean includingFilename;

  private String rowNumField;

  private boolean headerPresent;

  private String delimiter;
  private String enclosure;

  private String bufferSize;

  private boolean lazyConversionActive;

  private TextFileInputField[] inputFields;

  private boolean isaddresult;

  private boolean runningInParallel;

  private String encoding;

  private boolean newlinePossibleInFields;

  public CsvInputMeta() {
    super(TRANSFORM_ATTRIBUTES_FILE); // load transform-attributes.xml
    allocate( 0 );
  }

  @Override
  public void loadXML( Node transformNode, IMetaStore metaStore ) throws HopXMLException {
    readData( transformNode );
  }

  @Override
  public Object clone() {
    final CsvInputMeta retval = (CsvInputMeta) super.clone();
    retval.inputFields = new TextFileInputField[ inputFields.length ];
    for ( int i = 0; i < inputFields.length; i++ ) {
      retval.inputFields[ i ] = (TextFileInputField) inputFields[ i ].clone();
    }
    return retval;
  }

  @Override
  public void setDefault() {
    delimiter = ",";
    enclosure = "\"";
    headerPresent = true;
    lazyConversionActive = true;
    isaddresult = false;
    bufferSize = "50000";
  }

  private void readData( Node transformNode ) throws HopXMLException {
    try {
      filename = XMLHandler.getTagValue( transformNode, getXmlCode( "FILENAME" ) );
      filenameField = XMLHandler.getTagValue( transformNode, getXmlCode( "FILENAME_FIELD" ) );
      rowNumField = XMLHandler.getTagValue( transformNode, getXmlCode( "ROW_NUM_FIELD" ) );
      includingFilename =
        "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, getXmlCode( "INCLUDE_FILENAME" ) ) );
      delimiter = XMLHandler.getTagValue( transformNode, getXmlCode( "DELIMITER" ) );
      enclosure = XMLHandler.getTagValue( transformNode, getXmlCode( "ENCLOSURE" ) );
      bufferSize = XMLHandler.getTagValue( transformNode, getXmlCode( "BUFFERSIZE" ) );
      headerPresent = "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, getXmlCode( "HEADER_PRESENT" ) ) );
      lazyConversionActive =
        "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, getXmlCode( "LAZY_CONVERSION" ) ) );
      isaddresult = "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, getXmlCode( "ADD_FILENAME_RESULT" ) ) );
      runningInParallel = "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, getXmlCode( "PARALLEL" ) ) );
      String nlp = XMLHandler.getTagValue( transformNode, getXmlCode( "NEWLINE_POSSIBLE" ) );
      if ( Utils.isEmpty( nlp ) ) {
        if ( runningInParallel ) {
          newlinePossibleInFields = false;
        } else {
          newlinePossibleInFields = true;
        }
      } else {
        newlinePossibleInFields = "Y".equalsIgnoreCase( nlp );
      }
      encoding = XMLHandler.getTagValue( transformNode, getXmlCode( "ENCODING" ) );

      Node fields = XMLHandler.getSubNode( transformNode, getXmlCode( "FIELDS" ) );
      int nrFields = XMLHandler.countNodes( fields, getXmlCode( "FIELD" ) );

      allocate( nrFields );

      for ( int i = 0; i < nrFields; i++ ) {
        inputFields[ i ] = new TextFileInputField();

        Node fnode = XMLHandler.getSubNodeByNr( fields, getXmlCode( "FIELD" ), i );

        inputFields[ i ].setName( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_NAME" ) ) );
        inputFields[ i ].setType(
          ValueMetaFactory.getIdForValueMeta( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_TYPE" ) ) ) );
        inputFields[ i ].setFormat( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_FORMAT" ) ) );
        inputFields[ i ].setCurrencySymbol( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_CURRENCY" ) ) );
        inputFields[ i ].setDecimalSymbol( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_DECIMAL" ) ) );
        inputFields[ i ].setGroupSymbol( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_GROUP" ) ) );
        inputFields[ i ]
          .setLength( Const.toInt( XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_LENGTH" ) ), -1 ) );
        inputFields[ i ].setPrecision( Const.toInt(
          XMLHandler.getTagValue( fnode, getXmlCode( "FIELD_PRECISION" ) ), -1 ) );
        inputFields[ i ].setTrimType( ValueMetaString.getTrimTypeByCode( XMLHandler.getTagValue(
          fnode, getXmlCode( "FIELD_TRIM_TYPE" ) ) ) );
      }
    } catch ( Exception e ) {
      throw new HopXMLException( "Unable to load transform info from XML", e );
    }
  }

  public void allocate( int nrFields ) {
    inputFields = new TextFileInputField[ nrFields ];
  }

  @Override
  public String getXML() {
    StringBuilder retval = new StringBuilder( 500 );

    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "FILENAME" ), filename ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "FILENAME_FIELD" ), filenameField ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "ROW_NUM_FIELD" ), rowNumField ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "INCLUDE_FILENAME" ), includingFilename ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "DELIMITER" ), delimiter ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "ENCLOSURE" ), enclosure ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "HEADER_PRESENT" ), headerPresent ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "BUFFERSIZE" ), bufferSize ) );
    retval
      .append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "LAZY_CONVERSION" ), lazyConversionActive ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "ADD_FILENAME_RESULT" ), isaddresult ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "PARALLEL" ), runningInParallel ) );
    retval.append( "    " ).append(
      XMLHandler.addTagValue( getXmlCode( "NEWLINE_POSSIBLE" ), newlinePossibleInFields ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( getXmlCode( "ENCODING" ), encoding ) );

    retval.append( "    " ).append( XMLHandler.openTag( getXmlCode( "FIELDS" ) ) ).append( Const.CR );
    for ( int i = 0; i < inputFields.length; i++ ) {
      TextFileInputField field = inputFields[ i ];

      retval.append( "      " ).append( XMLHandler.openTag( getXmlCode( "FIELD" ) ) ).append( Const.CR );
      retval.append( "        " ).append( XMLHandler.addTagValue( getXmlCode( "FIELD_NAME" ), field.getName() ) );
      retval.append( "        " ).append(
        XMLHandler.addTagValue( getXmlCode( "FIELD_TYPE" ), ValueMetaFactory.getValueMetaName( field.getType() ) ) );
      retval
        .append( "        " ).append( XMLHandler.addTagValue( getXmlCode( "FIELD_FORMAT" ), field.getFormat() ) );
      retval.append( "        " ).append(
        XMLHandler.addTagValue( getXmlCode( "FIELD_CURRENCY" ), field.getCurrencySymbol() ) );
      retval.append( "        " ).append(
        XMLHandler.addTagValue( getXmlCode( "FIELD_DECIMAL" ), field.getDecimalSymbol() ) );
      retval.append( "        " ).append(
        XMLHandler.addTagValue( getXmlCode( "FIELD_GROUP" ), field.getGroupSymbol() ) );
      retval
        .append( "        " ).append( XMLHandler.addTagValue( getXmlCode( "FIELD_LENGTH" ), field.getLength() ) );
      retval.append( "        " ).append(
        XMLHandler.addTagValue( getXmlCode( "FIELD_PRECISION" ), field.getPrecision() ) );
      retval.append( "        " ).append(
        XMLHandler
          .addTagValue( getXmlCode( "FIELD_TRIM_TYPE" ), ValueMetaString.getTrimTypeCode( field.getTrimType() ) ) );
      retval.append( "      " ).append( XMLHandler.closeTag( getXmlCode( "FIELD" ) ) ).append( Const.CR );
    }
    retval.append( "    " ).append( XMLHandler.closeTag( getXmlCode( "FIELDS" ) ) ).append( Const.CR );

    return retval.toString();
  }

  @Override
  public void getFields( RowMetaInterface rowMeta, String origin, RowMetaInterface[] info, TransformMeta nextTransform,
                         VariableSpace space, IMetaStore metaStore ) throws HopTransformException {
    try {
      rowMeta.clear(); // Start with a clean slate, eats the input

      for ( int i = 0; i < inputFields.length; i++ ) {
        TextFileInputField field = inputFields[ i ];

        ValueMetaInterface valueMeta = ValueMetaFactory.createValueMeta( field.getName(), field.getType() );
        valueMeta.setConversionMask( field.getFormat() );
        valueMeta.setLength( field.getLength() );
        valueMeta.setPrecision( field.getPrecision() );
        valueMeta.setConversionMask( field.getFormat() );
        valueMeta.setDecimalSymbol( field.getDecimalSymbol() );
        valueMeta.setGroupingSymbol( field.getGroupSymbol() );
        valueMeta.setCurrencySymbol( field.getCurrencySymbol() );
        valueMeta.setTrimType( field.getTrimType() );
        if ( lazyConversionActive ) {
          valueMeta.setStorageType( ValueMetaInterface.STORAGE_TYPE_BINARY_STRING );
        }
        valueMeta.setStringEncoding( space.environmentSubstitute( encoding ) );

        // In case we want to convert Strings...
        // Using a copy of the valueMeta object means that the inner and outer representation format is the same.
        // Preview will show the data the same way as we read it.
        // This layout is then taken further down the road by the metadata through the pipeline.
        //
        ValueMetaInterface storageMetadata =
          ValueMetaFactory.cloneValueMeta( valueMeta, ValueMetaInterface.TYPE_STRING );
        storageMetadata.setStorageType( ValueMetaInterface.STORAGE_TYPE_NORMAL );
        storageMetadata.setLength( -1, -1 ); // we don't really know the lengths of the strings read in advance.
        valueMeta.setStorageMetadata( storageMetadata );

        valueMeta.setOrigin( origin );

        rowMeta.addValueMeta( valueMeta );
      }

      if ( !Utils.isEmpty( filenameField ) && includingFilename ) {
        ValueMetaInterface filenameMeta = new ValueMetaString( filenameField );
        filenameMeta.setOrigin( origin );
        if ( lazyConversionActive ) {
          filenameMeta.setStorageType( ValueMetaInterface.STORAGE_TYPE_BINARY_STRING );
          filenameMeta.setStorageMetadata( new ValueMetaString( filenameField ) );
        }
        rowMeta.addValueMeta( filenameMeta );
      }

      if ( !Utils.isEmpty( rowNumField ) ) {
        ValueMetaInterface rowNumMeta = new ValueMetaInteger( rowNumField );
        rowNumMeta.setLength( 10 );
        rowNumMeta.setOrigin( origin );
        rowMeta.addValueMeta( rowNumMeta );
      }
    } catch ( Exception e ) {
      throw new HopTransformException( e );
    }

  }

  @Override
  public void check( List<CheckResultInterface> remarks, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                     RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
                     IMetaStore metaStore ) {
    CheckResult cr;
    if ( prev == null || prev.size() == 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "CsvInputMeta.CheckResult.NotReceivingFields" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "CsvInputMeta.CheckResult.TransformRecevingData", prev.size() + "" ), transformMeta );
      remarks.add( cr );
    }

    // See if we have input streams leading to this transform!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "CsvInputMeta.CheckResult.TransformRecevingData2" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "CsvInputMeta.CheckResult.NoInputReceivedFromOtherTransforms" ), transformMeta );
      remarks.add( cr );
    }
  }

  @Override
  public CsvInput createTransform( TransformMeta transformMeta, CsvInputData transformDataInterface, int cnr, PipelineMeta tr,
                                   Pipeline pipeline ) {
    return new CsvInput( transformMeta, transformDataInterface, cnr, tr, pipeline );
  }

  @Override
  public CsvInputData getTransformData() {
    return new CsvInputData();
  }

  /**
   * @return the delimiter
   */
  public String getDelimiter() {
    return delimiter;
  }

  /**
   * @param delimiter the delimiter to set
   */
  public void setDelimiter( String delimiter ) {
    this.delimiter = delimiter;
  }

  /**
   * @return the filename
   */
  public String getFilename() {
    return filename;
  }

  /**
   * @param filename the filename to set
   */
  public void setFilename( String filename ) {
    this.filename = filename;
  }

  /**
   * @return the bufferSize
   */
  public String getBufferSize() {
    return bufferSize;
  }

  /**
   * @param bufferSize the bufferSize to set
   */
  public void setBufferSize( String bufferSize ) {
    this.bufferSize = bufferSize;
  }

  /**
   * @return true if lazy conversion is turned on: conversions are delayed as long as possible, perhaps to never occur
   * at all.
   */
  public boolean isLazyConversionActive() {
    return lazyConversionActive;
  }

  /**
   * @param lazyConversionActive true if lazy conversion is to be turned on: conversions are delayed as long as possible, perhaps to never
   *                             occur at all.
   */
  public void setLazyConversionActive( boolean lazyConversionActive ) {
    this.lazyConversionActive = lazyConversionActive;
  }

  /**
   * @return the headerPresent
   */
  public boolean isHeaderPresent() {
    return headerPresent;
  }

  /**
   * @param headerPresent the headerPresent to set
   */
  public void setHeaderPresent( boolean headerPresent ) {
    this.headerPresent = headerPresent;
  }

  /**
   * @return the enclosure
   */
  @Override
  public String getEnclosure() {
    return enclosure;
  }

  /**
   * @param enclosure the enclosure to set
   */
  public void setEnclosure( String enclosure ) {
    this.enclosure = enclosure;
  }

  @Override
  public List<ResourceReference> getResourceDependencies( PipelineMeta pipelineMeta, TransformMeta transformInfo ) {
    List<ResourceReference> references = new ArrayList<ResourceReference>( 5 );

    ResourceReference reference = new ResourceReference( transformInfo );
    references.add( reference );
    if ( !Utils.isEmpty( filename ) ) {
      // Add the filename to the references, including a reference to this
      // transform meta data.
      //
      reference.getEntries().add(
        new ResourceEntry( pipelineMeta.environmentSubstitute( filename ), ResourceType.FILE ) );
    }
    return references;
  }

  /**
   * @return the inputFields
   */
  @Override
  public TextFileInputField[] getInputFields() {
    return inputFields;
  }

  /**
   * @param inputFields the inputFields to set
   */
  public void setInputFields( TextFileInputField[] inputFields ) {
    this.inputFields = inputFields;
  }

  @Override
  public int getFileFormatTypeNr() {
    return TextFileInputMeta.FILE_FORMAT_MIXED; // TODO: check this
  }

  @Override
  public String[] getFilePaths( VariableSpace space ) {
    return new String[] { space.environmentSubstitute( filename ), };
  }

  @Override
  public int getNrHeaderLines() {
    return 1;
  }

  @Override
  public boolean hasHeader() {
    return isHeaderPresent();
  }

  @Override
  public String getErrorCountField() {
    return null;
  }

  @Override
  public String getErrorFieldsField() {
    return null;
  }

  @Override
  public String getErrorTextField() {
    return null;
  }

  @Override
  public String getEscapeCharacter() {
    return null;
  }

  @Override
  public String getFileType() {
    return "CSV";
  }

  @Override
  public String getSeparator() {
    return delimiter;
  }

  @Override
  public boolean includeFilename() {
    return false;
  }

  @Override
  public boolean includeRowNumber() {
    return false;
  }

  @Override
  public boolean isErrorIgnored() {
    return false;
  }

  @Override
  public boolean isErrorLineSkipped() {
    return false;
  }

  /**
   * @return the filenameField
   */
  public String getFilenameField() {
    return filenameField;
  }

  /**
   * @param filenameField the filenameField to set
   */
  public void setFilenameField( String filenameField ) {
    this.filenameField = filenameField;
  }

  /**
   * @return the includingFilename
   */
  public boolean isIncludingFilename() {
    return includingFilename;
  }

  /**
   * @param includingFilename the includingFilename to set
   */
  public void setIncludingFilename( boolean includingFilename ) {
    this.includingFilename = includingFilename;
  }

  /**
   * @return the rowNumField
   */
  public String getRowNumField() {
    return rowNumField;
  }

  /**
   * @param rowNumField the rowNumField to set
   */
  public void setRowNumField( String rowNumField ) {
    this.rowNumField = rowNumField;
  }

  /**
   * @param isaddresult The isaddresult to set.
   */
  public void setAddResultFile( boolean isaddresult ) {
    this.isaddresult = isaddresult;
  }

  /**
   * @return Returns isaddresult.
   */
  public boolean isAddResultFile() {
    return isaddresult;
  }

  /**
   * @return the runningInParallel
   */
  public boolean isRunningInParallel() {
    return runningInParallel;
  }

  /**
   * @param runningInParallel the runningInParallel to set
   */
  public void setRunningInParallel( boolean runningInParallel ) {
    this.runningInParallel = runningInParallel;
  }

  /**
   * @return the encoding
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * @param encoding the encoding to set
   */
  public void setEncoding( String encoding ) {
    this.encoding = encoding;
  }

  /**
   * @param space                   the variable space to use
   * @param definitions
   * @param resourceNamingInterface
   * @param metaStore               the metaStore in which non-kettle metadata could reside.
   * @return the filename of the exported resource
   */
  @Override
  public String exportResources( VariableSpace space, Map<String, ResourceDefinition> definitions,
                                 ResourceNamingInterface resourceNamingInterface, IMetaStore metaStore ) throws HopException {
    try {
      // The object that we're modifying here is a copy of the original!
      // So let's change the filename from relative to absolute by grabbing the file object...
      // In case the name of the file comes from previous transforms, forget about this!
      //
      if ( Utils.isEmpty( filenameField ) && !Utils.isEmpty( filename ) ) {
        // From : ${Internal.Pipeline.Filename.Directory}/../foo/bar.csv
        // To : /home/matt/test/files/foo/bar.csv
        //
        FileObject fileObject = HopVFS.getFileObject( space.environmentSubstitute( filename ), space );

        // If the file doesn't exist, forget about this effort too!
        //
        if ( fileObject.exists() ) {
          // Convert to an absolute path...
          //
          filename = resourceNamingInterface.nameResource( fileObject, space, true );

          return filename;
        }
      }
      return null;
    } catch ( Exception e ) {
      throw new HopException( e );
    }
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }

  /**
   * @return the newlinePossibleInFields
   */
  public boolean isNewlinePossibleInFields() {
    return newlinePossibleInFields;
  }

  /**
   * @param newlinePossibleInFields the newlinePossibleInFields to set
   */
  public void setNewlinePossibleInFields( boolean newlinePossibleInFields ) {
    this.newlinePossibleInFields = newlinePossibleInFields;
  }

  @Override
  public FileObject getHeaderFileObject( final PipelineMeta pipelineMeta ) {
    final String filename = pipelineMeta.environmentSubstitute( getFilename() );
    try {
      return HopVFS.getFileObject( filename );
    } catch ( final HopFileException e ) {
      return null;
    }
  }


  @Override
  public String getDialogClassName(){

    return CsvInputDialog.class.getName();
  }
}