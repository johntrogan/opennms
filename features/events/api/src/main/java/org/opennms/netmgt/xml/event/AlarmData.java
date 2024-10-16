/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.xml.event;

  //---------------------------------/
 //- Imported classes and packages -/
//---------------------------------/

import org.opennms.netmgt.events.api.model.IAlarmData;

import io.swagger.v3.oas.annotations.Hidden;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This element is used for converting events into alarms.
 * 
 */

@XmlRootElement(name="alarm-data")
@XmlAccessorType(XmlAccessType.FIELD)
//@ValidateUsing("event.xsd")
public class AlarmData implements Serializable {
	private static final long serialVersionUID = 3681502418413339216L;


    /**
     * Field _reductionKey.
     */
	@XmlAttribute(name="reduction-key", required=true)
	@NotNull
    private java.lang.String _reductionKey;

    /**
     * Field _alarmType.
     */
	@XmlAttribute(name="alarm-type", required=true)
	@NotNull
	@Min(1)
    private Integer _alarmType;

    /**
     * Field _clearKey.
     */
	@XmlAttribute(name="clear-key")
    private java.lang.String _clearKey;

    /**
     * Field _autoClean.
     */
	@XmlAttribute(name="auto-clean")
    private Boolean _autoClean = false;

    /**
     * Field _x733AlarmType.
     */
	@XmlAttribute(name="x733-alarm-type")
    private java.lang.String _x733AlarmType;

    /**
     * Field _x733ProbableCause.
     */
	@XmlAttribute(name="x733-probable-cause")
    private Integer _x733ProbableCause;
	
	/**
	 * Field m_updateField
	 */
    @XmlElement(name="update-field", required=false)
    @Valid
    private List<UpdateField> m_updateFieldList = new ArrayList<>();

    /**
     * Field m_managedObject
     */
    @XmlElement(name="managed-object", required=false)
    private ManagedObject m_managedObject;


    public AlarmData() {
        super();
    }

    public static AlarmData copyFrom(IAlarmData source) {
        if (source == null) {
            return null;
        }

        AlarmData alarmData = new AlarmData();
        alarmData.setReductionKey(source.getReductionKey());
        alarmData.setAlarmType(source.hasAlarmType() ? source.getAlarmType() : null);
        alarmData.setClearKey(source.getClearKey());
        alarmData.setAutoClean(source.hasAutoClean() ? source.getAutoClean() : null);
        alarmData.setX733AlarmType(source.getX733AlarmType());
        alarmData.setX733ProbableCause(source.hasX733ProbableCause() ? source.getX733ProbableCause() : null);
        alarmData.getUpdateFieldList().addAll(
                source.getUpdateFieldList().stream().map(UpdateField::copyFrom).collect(Collectors.toList()));
        alarmData.setManagedObject(ManagedObject.copyFrom(source.getManagedObject()));
        return alarmData;
    }

    public void deleteAlarmType(
    ) {
    	this._alarmType = null;
    }

    /**
     */
    public void deleteAutoClean(
    ) {
        this._autoClean = null;
    }

    /**
     */
    public void deleteX733ProbableCause(
    ) {
        this._x733ProbableCause = null;
    }

    /**
     * Returns the value of field 'alarmType'.
     * 
     * @return the value of field 'AlarmType'.
     */
    public Integer getAlarmType() {
        return this._alarmType == null? 0 : this._alarmType;
    }

    /**
     * Returns the value of field 'autoClean'.
     * 
     * @return the value of field 'AutoClean'.
     */
    public Boolean getAutoClean() {
        return this._autoClean == null? false : this._autoClean;
    }

    /**
     * Returns the value of field 'clearKey'.
     * 
     * @return the value of field 'ClearKey'.
     */
    public java.lang.String getClearKey() {
        return this._clearKey;
    }

    /**
     * Returns the value of field 'reductionKey'.
     * 
     * @return the value of field 'ReductionKey'.
     */
    public java.lang.String getReductionKey(
    ) {
        return this._reductionKey;
    }

    /**
     * Returns the value of field 'x733AlarmType'.
     * 
     * @return the value of field 'X733AlarmType'.
     */
    public java.lang.String getX733AlarmType(
    ) {
        return this._x733AlarmType;
    }

    /**
     * Returns the value of field 'x733ProbableCause'.
     * 
     * @return the value of field 'X733ProbableCause'.
     */
    public Integer getX733ProbableCause() {
        return this._x733ProbableCause == null ? 0 : this._x733ProbableCause;
    }

    /**
     * Method hasAlarmType.
     * 
     * @return true if at least one AlarmType has been added
     */
    public boolean hasAlarmType(
    ) {
        return this._alarmType != null;
    }

    /**
     * Method hasAutoClean.
     * 
     * @return true if at least one AutoClean has been added
     */
    public boolean hasAutoClean(
    ) {
        return this._autoClean != null;
    }

    /**
     * Method hasX733ProbableCause.
     * 
     * @return true if at least one X733ProbableCause has been added
     */
    public boolean hasX733ProbableCause(
    ) {
        return this._x733ProbableCause != null;
    }

    /**
     * Returns the value of field 'autoClean'.
     * 
     * @return the value of field 'AutoClean'.
     */
    public Boolean isAutoClean(
    ) {
        return getAutoClean();
    }

    /**
     * Sets the value of field 'alarmType'.
     * 
     * @param alarmType the value of field 'alarmType'.
     */
    public void setAlarmType(
            final Integer alarmType) {
        this._alarmType = alarmType;
    }

    /**
     * Sets the value of field 'autoClean'.
     * 
     * @param autoClean the value of field 'autoClean'.
     */
    public void setAutoClean(
            final Boolean autoClean) {
        this._autoClean = autoClean;
    }

    /**
     * Sets the value of field 'clearKey'.
     * 
     * @param clearKey the value of field 'clearKey'.
     */
    public void setClearKey(
            final java.lang.String clearKey) {
        this._clearKey = clearKey;
    }

    /**
     * Sets the value of field 'reductionKey'.
     * 
     * @param reductionKey the value of field 'reductionKey'.
     */
    public void setReductionKey(
            final java.lang.String reductionKey) {
        this._reductionKey = reductionKey;
    }

    /**
     * Sets the value of field 'x733AlarmType'.
     * 
     * @param x733AlarmType the value of field 'x733AlarmType'.
     */
    public void setX733AlarmType(
            final java.lang.String x733AlarmType) {
        this._x733AlarmType = x733AlarmType;
    }

    /**
     * Sets the value of field 'x733ProbableCause'.
     * 
     * @param x733ProbableCause the value of field
     * 'x733ProbableCause'.
     */
    public void setX733ProbableCause(
            final Integer x733ProbableCause) {
        this._x733ProbableCause = x733ProbableCause;
    }
    
    public UpdateField[] getUpdateField() {
        return m_updateFieldList.toArray(new UpdateField[0]);
    }
    
    public Collection<UpdateField> getUpdateFieldCollection() {
        return m_updateFieldList;
    }
    
    public List<UpdateField> getUpdateFieldList() {
        return m_updateFieldList;
    }
    
    public int getUpdateFieldListCount() {
        return m_updateFieldList.size();
    }

    public Boolean hasUpdateFields() {
        Boolean hasFields = true;
        if (m_updateFieldList == null || m_updateFieldList.isEmpty()) {
            hasFields = false;
        }
        return hasFields;
    }
    
    /** @deprecated */
    @Hidden
    public void setUpdateField(UpdateField[] fields) {
        m_updateFieldList.clear();
        for (int i = 0; i < fields.length; i++) {
            m_updateFieldList.add(fields[i]);
        }
    }
    
    public void setUpdateField(final List<UpdateField> fields) {
        if (m_updateFieldList == fields) return;
        m_updateFieldList.clear();
        m_updateFieldList.addAll(fields);
    }
    
    /** @deprecated */
    @Hidden
    public void setUpdateFieldCollection(final Collection<UpdateField> fields) {
        if (m_updateFieldList == fields) return;
        m_updateFieldList.clear();
        m_updateFieldList.addAll(fields);
    }


    public ManagedObject getManagedObject() {
        return m_managedObject;
    }

    public void setManagedObject(ManagedObject m_managedObject) {
        this.m_managedObject = m_managedObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlarmData alarmData = (AlarmData) o;
        return Objects.equals(_reductionKey, alarmData._reductionKey) &&
                Objects.equals(_alarmType, alarmData._alarmType) &&
                Objects.equals(_clearKey, alarmData._clearKey) &&
                Objects.equals(_autoClean, alarmData._autoClean) &&
                Objects.equals(_x733AlarmType, alarmData._x733AlarmType) &&
                Objects.equals(_x733ProbableCause, alarmData._x733ProbableCause) &&
                Objects.equals(m_updateFieldList, alarmData.m_updateFieldList) &&
                Objects.equals(m_managedObject, alarmData.m_managedObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_reductionKey, _alarmType, _clearKey, _autoClean, _x733AlarmType, _x733ProbableCause, m_updateFieldList, m_managedObject);
    }

    @Override
    public String toString() {
    	return new OnmsStringBuilder(this).toString();
    }
}
