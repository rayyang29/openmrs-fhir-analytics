// Copyright 2020-2022 Google LLC
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

import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.ListCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;

public class JdbcFetchHapi {
	
	private JdbcConnectionUtil jdbcConnectionUtil;
	
	JdbcFetchHapi(JdbcConnectionUtil jdbcConnectionUtil) {
		this.jdbcConnectionUtil = jdbcConnectionUtil;
	}
	
	/**
	 * RowMapper class implementation for JdbcIo direct fetch with HAPI as the source FHIR server. Each
	 * element in the ResultSet returned by the query maps to a List of String objects corresponding to
	 * the column values in the query result.
	 */
	public static class ResultSetToList implements JdbcIO.RowMapper<List<String>> {
		
		@Override
		public List<String> mapRow(ResultSet resultSet) throws Exception {
			String jsonResource = "";
			
			switch (resultSet.getString("res_encoding")) {
				
				case "JSON":
					jsonResource = new String(resultSet.getBytes("res_text"), Charsets.UTF_8);
					break;
				case "JSONC":
					Blob blob = resultSet.getBlob("res_text");
					jsonResource = GZipUtil.decompress(blob.getBytes(1, (int) blob.length()));
					blob.free();
					break;
				case "DEL":
					break;
			}
			
			String resourceId = resultSet.getString("res_id");
			String resourceType = resultSet.getString("res_type");
			String lastUpdated = resultSet.getString("res_updated");
			String resourceVersion = resultSet.getString("res_ver");
			return Arrays.asList(resourceId, resourceType, resourceVersion, lastUpdated, jsonResource);
		}
	}
	
	/**
	 * Utilizes Beam JdbcIO to query for resources directly from FHIR (HAPI) server's database and
	 * returns a PCollection of Lists of String objects - each corresponding to a resource's payload
	 */
	public static class FetchRowsJdbcIo extends PTransform<PCollection<List<String>>, PCollection<List<String>>> {
		
		private final JdbcIO.DataSourceConfiguration dataSourceConfig;
		
		public FetchRowsJdbcIo(JdbcIO.DataSourceConfiguration dataSourceConfig) {
			this.dataSourceConfig = dataSourceConfig;
		}
		
		@Override
		public PCollection<List<String>> expand(PCollection<List<String>> queryParameters) {
			return queryParameters.apply("JdbcIO readAll",
			    JdbcIO.<List<String>, List<String>> readAll().withDataSourceConfiguration(dataSourceConfig)
			            .withCoder(ListCoder.of(StringUtf8Coder.of()))
			            .withParameterSetter(new JdbcIO.PreparedStatementSetter<List<String>>() {
				            
				            @Override
				            public void setParameters(List<String> element, PreparedStatement preparedStatement)
				                    throws Exception {
					            preparedStatement.setString(1, element.get(0));
					            preparedStatement.setInt(2, Integer.valueOf(element.get(1)));
					            preparedStatement.setInt(3, Integer.valueOf(element.get(2)));
				            }
			            }).withOutputParallelization(true)
			            .withQuery(
			                "SELECT res.res_id, res.res_type, res.res_updated, res.res_ver, ver.res_encoding, ver.res_text "
			                        + "FROM hfj_resource res, hfj_res_ver ver "
			                        + "WHERE res.res_type=? AND res.res_id = ver.res_id AND res.res_id %? = ?")
			            .withRowMapper(new ResultSetToList()));
			
		}
	}
	
	JdbcIO.DataSourceConfiguration getJdbcConfig() {
		return JdbcIO.DataSourceConfiguration.create(this.jdbcConnectionUtil.getConnectionObject());
	}
	
	/**
	 * Generates the query parameters for the JdbcIO fetch. The query parameters are generated based on
	 * the maximum Jdbc pool size and the batch size options set for the job.
	 * 
	 * @param pipeline the pipeline object
	 * @param options the pipeline options
	 * @param resourceType the resource type
	 * @param numResources total number of resources of the given type
	 * @param batchNum the relevant batch number
	 * @return a list of query parameters in the form of lists of strings
	 */
	@VisibleForTesting
	List<List<String>> generateQueryParameters(Pipeline pipeline, FhirEtlOptions options, String resourceType,
	        int numResources, int numBatches, int batchNum) {
		int jdbcMaxPoolSize = options.getJdbcMaxPoolSize();
		List<List<String>> queryParameterList = new ArrayList<List<String>>();
		int totalParallelization = numBatches * jdbcMaxPoolSize;
		
		for (int i = 0; i < jdbcMaxPoolSize; i++) {
			queryParameterList.add(Arrays.asList(resourceType, String.valueOf(totalParallelization),
			    String.valueOf(i + batchNum * jdbcMaxPoolSize)));
		}
		
		return queryParameterList;
	}
	
}
