/**
 *  Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.sequences.common.to;

/**
 * Transfer object representing the information required to view a sequence in the list
 * sequences user interface
 */
public class SequenceInfo {

    private String name;
    private boolean enableStatistics;
    private boolean enableTracing;
    private String description;
    private String artifactContainerName;
    private boolean isEdited;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnableStatistics() {
        return enableStatistics;
    }

    public void setEnableStatistics(boolean enableStatistics) {
        this.enableStatistics = enableStatistics;
    }

    public boolean isEnableTracing() {
        return enableTracing;
    }

    public void setEnableTracing(boolean enableTracing) {
        this.enableTracing = enableTracing;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the artifactContainer of the sequence which deployed from
     * @return sequence description
     */
    public String getArtifactContainerName() {
        return artifactContainerName;
    }

    /**
     * Set the sequence artifact container name as a metadata
     * @param artifactContainerName endpoint name
     */
    public void setArtifactContainerName(String artifactContainerName) {
        this.artifactContainerName = artifactContainerName;
    }

    /**
     * Get the edit state of the sequence
     * @return sequence description
     */
    public boolean getIsEdited() {
        return isEdited;
    }

    /**
     * Set the edit state of the sequence as a metadata
     * @param isEdited sequence name
     */
    public void setIsEdited(boolean isEdited) {
        this.isEdited = isEdited;
    }
}
