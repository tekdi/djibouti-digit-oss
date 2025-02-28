package org.egov.bpa.repository.rowmapper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.egov.bpa.web.model.AuditDetails;
import org.egov.bpa.web.model.BPA;
import org.egov.bpa.web.model.BuildingInfo;
import org.egov.bpa.web.model.Document;
import org.egov.bpa.web.model.FloorInfo;
import org.egov.bpa.web.model.PlotInfo;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BPARowMapper implements ResultSetExtractor<List<BPA>> {

	@Autowired
	private ObjectMapper mapper;

	/**
	 * extract the data from the resultset and prepare the BPA Object
	 * 
	 * @see org.springframework.jdbc.core.ResultSetExtractor#extractData(java.sql.ResultSet)
	 */
	@Override
	public List<BPA> extractData(ResultSet rs) throws SQLException, DataAccessException {

		Map<String, BPA> buildingMap = new LinkedHashMap<>();

		while (rs.next()) {
			String id = rs.getString("bpa_id");
			String applicationNo = rs.getString("applicationno");
			String approvalNo = rs.getString("approvalNo");
			BPA currentbpa = buildingMap.get(id);
			String tenantId = rs.getString("bpa_tenantId");

			if (currentbpa == null) {
				String additionalDetailsStr = rs.getString("additionalDetails");
				Object additionalDetails = new Gson()
						.fromJson(additionalDetailsStr.equals("{}") || additionalDetailsStr.equals("null") ? null
								: additionalDetailsStr, Object.class);

				currentbpa = BPA.builder().auditDetails(buildAuditDetails(rs)).applicationNo(applicationNo)
						.status(rs.getString("status")).tenantId(tenantId).approvalNo(approvalNo)
						.approvalDate(rs.getLong("approvalDate")).accountId(rs.getString("accountId"))
						.landId(rs.getString("landId")).applicationDate(rs.getLong("applicationDate")).id(id)
						.additionalDetails(additionalDetails).businessService(rs.getString("businessService")).build();

				buildingMap.put(id, currentbpa);
			}
			addChildrenToProperty(rs, currentbpa);
		}
		return new ArrayList<>(buildingMap.values());
	}

	/**
	 * add child objects to the BPA fro the results set
	 * 
	 * @param rs
	 * @param bpa
	 * @throws SQLException
	 */
	private void addChildrenToProperty(ResultSet rs, BPA bpa) throws SQLException {
		if (bpa == null) {
			return;
		}

		addPlotInfo(rs, bpa);
		addBuildingInfo(rs, bpa);
		addDocuments(rs, bpa);
	}

	private AuditDetails buildAuditDetails(ResultSet rs) throws SQLException {
		Long lastModifiedTime = rs.getLong("bpa_lastModifiedTime");
		if (rs.wasNull()) {
			lastModifiedTime = null;
		}
		return AuditDetails.builder().createdBy(rs.getString("bpa_createdBy"))
				.createdTime(rs.getLong("bpa_createdTime")).lastModifiedBy(rs.getString("bpa_lastModifiedBy"))
				.lastModifiedTime(lastModifiedTime).build();
	}

	private void addPlotInfo(ResultSet rs, BPA bpa) throws SQLException {
		String plotId = rs.getString("bpa_plot_id");
		if (StringUtils.isNotBlank(plotId)) {
			bpa.setPlotInfo(PlotInfo.builder().id(plotId).plotArea(rs.getDouble("bpa_plot_area"))
					.plotNumber(rs.getString("bpa_plot_number")).khataNumber(rs.getString("bpa_khata_number"))
					.additionalDetails(getAdditionalDetails(rs.getObject("bpa_plot_details"))).build());
		}
	}

	private void addBuildingInfo(ResultSet rs, BPA bpa) throws SQLException {
		String buildingId = rs.getString("bpa_building_id");
		if (StringUtils.isBlank(buildingId)) {
			return;
		}

		BuildingInfo buildingInfo;
		boolean isNotBuildingInfoExist = CollectionUtils.isEmpty(bpa.getBuildingInfos())
				|| bpa.getBuildingInfos().stream().noneMatch(b -> b.getId().equals(buildingId));

		if (isNotBuildingInfoExist) {
			buildingInfo = BuildingInfo.builder().id(buildingId)
					.totalBuiltupArea(rs.getDouble("bpa_total_builtup_area"))
					.numberOfFloors(rs.getInt("bpa_building_num_floor"))
					.buildingHeight(rs.getDouble("bpa_building_height"))
					.additionalDetails(getAdditionalDetails(rs.getObject("bpa_building_details"))).build();

			bpa.addBuildingInfoItem(buildingInfo);

		} else {
			buildingInfo = bpa.getBuildingInfos().stream().filter(bi -> bi.getId().equals(buildingId)).findAny()
					.orElse(null);
		}

		if (buildingInfo == null) {
			return;
		}
		addFloorInfo(rs, buildingInfo);
	}

	private void addFloorInfo(ResultSet rs, BuildingInfo buildingInfo) throws SQLException {
		String floorId = rs.getString("bpa_floor_id");
		if (StringUtils.isBlank(floorId)) {
			return;
		}

		FloorInfo floorInfo = FloorInfo.builder().id(floorId).floorName(rs.getString("bpa_floor_name"))
				.level(rs.getInt("bpa_floor_level")).usage(rs.getString("bpa_floor_usage"))
				.buildupArea(rs.getDouble("bpa_floor_buildup_area")).floorArea(rs.getDouble("bpa_floor_area"))
				.carpetArea(rs.getDouble("bpa_floor_carpet_area"))
				.additionalDetails(getAdditionalDetails(rs.getObject("bpa_floor_details"))).build();

		boolean isNotFloorInfoExist = CollectionUtils.isEmpty(buildingInfo.getFloorInfos())
				|| buildingInfo.getFloorInfos().stream().noneMatch(f -> f.getId().equals(floorId));

		if (isNotFloorInfoExist) {
			buildingInfo.addFloorInfoItem(floorInfo);
		}
	}

	private void addDocuments(ResultSet rs, BPA bpa) throws SQLException {
		final String docId = rs.getString("bpa_doc_id");
		if (StringUtils.isBlank(docId)) {
			return;
		}

		Document document = Document.builder().id(docId).documentType(rs.getString("bpa_doc_documenttype"))
				.fileStoreId(rs.getString("bpa_doc_filestore")).documentUid(rs.getString("documentUid"))
				.additionalDetails(getAdditionalDetails(rs.getObject("doc_details"))).build();

		boolean isNotDocExist = CollectionUtils.isEmpty(bpa.getDocuments())
				|| bpa.getDocuments().stream().noneMatch(f -> f.getId().equals(docId));

		if (isNotDocExist) {
			bpa.addDocumentsItem(document);
		}
	}

	private JsonNode getAdditionalDetails(Object additionaldetailsObj) {
		JsonNode additionalDetail = null;
		if (additionaldetailsObj == null) {
			return additionalDetail;
		}
		PGobject pgObj = (PGobject) additionaldetailsObj;
		String value = pgObj.getValue();
		if (StringUtils.isNotBlank(value)) {
			try {
				additionalDetail = mapper.readTree(value);
			} catch (IOException e) {
				log.error("Failed to parse additionalDetails", e);
			}
		}
		return additionalDetail;
	}
}
