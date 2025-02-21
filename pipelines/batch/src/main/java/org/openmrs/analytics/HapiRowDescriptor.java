// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.openmrs.analytics;

import java.io.Serializable;

import com.google.auto.value.AutoValue;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.coders.SerializableCoder;

@DefaultCoder(SerializableCoder.class)
@AutoValue
abstract class HapiRowDescriptor implements Serializable {
	
	static HapiRowDescriptor create(String resourceId, String resourceType, String lastUpdated, String resourceVersion,
	        String jsonResource) {
		return new AutoValue_HapiRowDescriptor(resourceId, resourceType, lastUpdated, resourceVersion, jsonResource);
	}
	
	abstract String resourceId();
	
	abstract String resourceType();
	
	abstract String lastUpdated();
	
	abstract String resourceVersion();
	
	abstract String jsonResource();
}
