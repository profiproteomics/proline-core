/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package fr.proline.core.om.model.msi.serializer;  
@SuppressWarnings("all")
public class MSISearch extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"MSISearch\",\"namespace\":\"fr.proline.core.om.model.msi.serializer\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"result_file_name\",\"type\":\"string\"},{\"name\":\"submitted_queries_count\",\"type\":\"int\"},{\"name\":\"search_settings\",\"type\":{\"type\":\"record\",\"name\":\"SearchSettings\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"software_name\",\"type\":\"string\"},{\"name\":\"software_version\",\"type\":\"string\"},{\"name\":\"taxonomy\",\"type\":\"string\"},{\"name\":\"max_missed_cleavages\",\"type\":\"int\"},{\"name\":\"ms1_charge_states\",\"type\":\"string\"},{\"name\":\"ms1_mass_error_tolerance\",\"type\":\"double\"},{\"name\":\"ms1_mass_error_tolerance_unit\",\"type\":\"string\"},{\"name\":\"is_decoy\",\"type\":\"boolean\"},{\"name\":\"used_enzymes\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},{\"name\":\"variable_ptm_defs\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},{\"name\":\"fixed_ptm_defs\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},{\"name\":\"seq_databases\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},{\"name\":\"instrument_config\",\"type\":{\"type\":\"record\",\"name\":\"InstrumentConfig\",\"fields\":[{\"name\":\"id\",\"type\":\"int\",\"default\":\"\"},{\"name\":\"name\",\"type\":\"string\",\"default\":\"\"},{\"name\":\"instrument\",\"type\":{\"type\":\"record\",\"name\":\"Instrument\",\"fields\":[{\"name\":\"id\",\"type\":\"int\",\"default\":\"\"},{\"name\":\"name\",\"type\":\"string\",\"default\":\"\"},{\"name\":\"source\",\"type\":\"string\"}]}},{\"name\":\"ms1_analyzer\",\"type\":\"string\",\"default\":\"\"},{\"name\":\"msn_analyzer\",\"type\":\"string\",\"default\":\"\"},{\"name\":\"activation_type\",\"type\":\"string\",\"default\":\"\"}]}},{\"name\":\"quantitation\",\"type\":\"string\"}]}}]}");
  @Deprecated public int id;
  @Deprecated public java.lang.CharSequence result_file_name;
  @Deprecated public int submitted_queries_count;
  @Deprecated public fr.proline.core.om.model.msi.serializer.SearchSettings search_settings;
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return id;
    case 1: return result_file_name;
    case 2: return submitted_queries_count;
    case 3: return search_settings;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: id = (java.lang.Integer)value$; break;
    case 1: result_file_name = (java.lang.CharSequence)value$; break;
    case 2: submitted_queries_count = (java.lang.Integer)value$; break;
    case 3: search_settings = (fr.proline.core.om.model.msi.serializer.SearchSettings)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'id' field.
   */
  public java.lang.Integer getId() {
    return id;
  }

  /**
   * Sets the value of the 'id' field.
   * @param value the value to set.
   */
  public void setId(java.lang.Integer value) {
    this.id = value;
  }

  /**
   * Gets the value of the 'result_file_name' field.
   */
  public java.lang.CharSequence getResultFileName() {
    return result_file_name;
  }

  /**
   * Sets the value of the 'result_file_name' field.
   * @param value the value to set.
   */
  public void setResultFileName(java.lang.CharSequence value) {
    this.result_file_name = value;
  }

  /**
   * Gets the value of the 'submitted_queries_count' field.
   */
  public java.lang.Integer getSubmittedQueriesCount() {
    return submitted_queries_count;
  }

  /**
   * Sets the value of the 'submitted_queries_count' field.
   * @param value the value to set.
   */
  public void setSubmittedQueriesCount(java.lang.Integer value) {
    this.submitted_queries_count = value;
  }

  /**
   * Gets the value of the 'search_settings' field.
   */
  public fr.proline.core.om.model.msi.serializer.SearchSettings getSearchSettings() {
    return search_settings;
  }

  /**
   * Sets the value of the 'search_settings' field.
   * @param value the value to set.
   */
  public void setSearchSettings(fr.proline.core.om.model.msi.serializer.SearchSettings value) {
    this.search_settings = value;
  }

  /** Creates a new MSISearch RecordBuilder */
  public static fr.proline.core.om.model.msi.serializer.MSISearch.Builder newBuilder() {
    return new fr.proline.core.om.model.msi.serializer.MSISearch.Builder();
  }
  
  /** Creates a new MSISearch RecordBuilder by copying an existing Builder */
  public static fr.proline.core.om.model.msi.serializer.MSISearch.Builder newBuilder(fr.proline.core.om.model.msi.serializer.MSISearch.Builder other) {
    return new fr.proline.core.om.model.msi.serializer.MSISearch.Builder(other);
  }
  
  /** Creates a new MSISearch RecordBuilder by copying an existing MSISearch instance */
  public static fr.proline.core.om.model.msi.serializer.MSISearch.Builder newBuilder(fr.proline.core.om.model.msi.serializer.MSISearch other) {
    return new fr.proline.core.om.model.msi.serializer.MSISearch.Builder(other);
  }
  
  /**
   * RecordBuilder for MSISearch instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<MSISearch>
    implements org.apache.avro.data.RecordBuilder<MSISearch> {

    private int id;
    private java.lang.CharSequence result_file_name;
    private int submitted_queries_count;
    private fr.proline.core.om.model.msi.serializer.SearchSettings search_settings;

    /** Creates a new Builder */
    private Builder() {
      super(fr.proline.core.om.model.msi.serializer.MSISearch.SCHEMA$);
    }
    
    /** Creates a Builder by copying an existing Builder */
    private Builder(fr.proline.core.om.model.msi.serializer.MSISearch.Builder other) {
      super(other);
    }
    
    /** Creates a Builder by copying an existing MSISearch instance */
    private Builder(fr.proline.core.om.model.msi.serializer.MSISearch other) {
            super(fr.proline.core.om.model.msi.serializer.MSISearch.SCHEMA$);
      if (isValidValue(fields()[0], other.id)) {
        this.id = (java.lang.Integer) data().deepCopy(fields()[0].schema(), other.id);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.result_file_name)) {
        this.result_file_name = (java.lang.CharSequence) data().deepCopy(fields()[1].schema(), other.result_file_name);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.submitted_queries_count)) {
        this.submitted_queries_count = (java.lang.Integer) data().deepCopy(fields()[2].schema(), other.submitted_queries_count);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.search_settings)) {
        this.search_settings = (fr.proline.core.om.model.msi.serializer.SearchSettings) data().deepCopy(fields()[3].schema(), other.search_settings);
        fieldSetFlags()[3] = true;
      }
    }

    /** Gets the value of the 'id' field */
    public java.lang.Integer getId() {
      return id;
    }
    
    /** Sets the value of the 'id' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder setId(int value) {
      validate(fields()[0], value);
      this.id = value;
      fieldSetFlags()[0] = true;
      return this; 
    }
    
    /** Checks whether the 'id' field has been set */
    public boolean hasId() {
      return fieldSetFlags()[0];
    }
    
    /** Clears the value of the 'id' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder clearId() {
      fieldSetFlags()[0] = false;
      return this;
    }

    /** Gets the value of the 'result_file_name' field */
    public java.lang.CharSequence getResultFileName() {
      return result_file_name;
    }
    
    /** Sets the value of the 'result_file_name' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder setResultFileName(java.lang.CharSequence value) {
      validate(fields()[1], value);
      this.result_file_name = value;
      fieldSetFlags()[1] = true;
      return this; 
    }
    
    /** Checks whether the 'result_file_name' field has been set */
    public boolean hasResultFileName() {
      return fieldSetFlags()[1];
    }
    
    /** Clears the value of the 'result_file_name' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder clearResultFileName() {
      result_file_name = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /** Gets the value of the 'submitted_queries_count' field */
    public java.lang.Integer getSubmittedQueriesCount() {
      return submitted_queries_count;
    }
    
    /** Sets the value of the 'submitted_queries_count' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder setSubmittedQueriesCount(int value) {
      validate(fields()[2], value);
      this.submitted_queries_count = value;
      fieldSetFlags()[2] = true;
      return this; 
    }
    
    /** Checks whether the 'submitted_queries_count' field has been set */
    public boolean hasSubmittedQueriesCount() {
      return fieldSetFlags()[2];
    }
    
    /** Clears the value of the 'submitted_queries_count' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder clearSubmittedQueriesCount() {
      fieldSetFlags()[2] = false;
      return this;
    }

    /** Gets the value of the 'search_settings' field */
    public fr.proline.core.om.model.msi.serializer.SearchSettings getSearchSettings() {
      return search_settings;
    }
    
    /** Sets the value of the 'search_settings' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder setSearchSettings(fr.proline.core.om.model.msi.serializer.SearchSettings value) {
      validate(fields()[3], value);
      this.search_settings = value;
      fieldSetFlags()[3] = true;
      return this; 
    }
    
    /** Checks whether the 'search_settings' field has been set */
    public boolean hasSearchSettings() {
      return fieldSetFlags()[3];
    }
    
    /** Clears the value of the 'search_settings' field */
    public fr.proline.core.om.model.msi.serializer.MSISearch.Builder clearSearchSettings() {
      search_settings = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    @Override
    public MSISearch build() {
      try {
        MSISearch record = new MSISearch();
        record.id = fieldSetFlags()[0] ? this.id : (java.lang.Integer) defaultValue(fields()[0]);
        record.result_file_name = fieldSetFlags()[1] ? this.result_file_name : (java.lang.CharSequence) defaultValue(fields()[1]);
        record.submitted_queries_count = fieldSetFlags()[2] ? this.submitted_queries_count : (java.lang.Integer) defaultValue(fields()[2]);
        record.search_settings = fieldSetFlags()[3] ? this.search_settings : (fr.proline.core.om.model.msi.serializer.SearchSettings) defaultValue(fields()[3]);
        return record;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }
}
