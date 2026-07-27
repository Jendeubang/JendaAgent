package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentAssetEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AgentAssetMapper extends BaseMapper<AgentAssetEntity> {
    @Insert("INSERT INTO agent_asset (asset_id, owner_user_id, session_id, run_id, file_name, media_type, size_bytes, object_key, image_url, source, created_at) VALUES (#{assetId}, #{ownerUserId}, #{sessionId}, #{runId}, #{fileName}, #{mediaType}, #{sizeBytes}, #{objectKey}, #{imageUrl}, #{source}, #{createdAt})")
    int insertAsset(AgentAssetEntity entity);

    @Update("UPDATE agent_asset SET owner_user_id = #{ownerUserId}, session_id = #{sessionId}, run_id = #{runId}, file_name = #{fileName}, media_type = #{mediaType}, size_bytes = #{sizeBytes}, object_key = #{objectKey}, image_url = #{imageUrl}, source = #{source}, created_at = #{createdAt} WHERE asset_id = #{assetId}")
    int updateAsset(AgentAssetEntity entity);

    @Select("SELECT COUNT(*) FROM agent_asset WHERE owner_user_id = #{ownerUserId}")
    long countOwned(@Param("ownerUserId") String ownerUserId);

    @Select("SELECT COUNT(*) FROM agent_asset WHERE owner_user_id = #{ownerUserId} AND session_id = #{sessionId}")
    long countOwnedInSession(@Param("ownerUserId") String ownerUserId, @Param("sessionId") String sessionId);

    @Select("SELECT * FROM agent_asset WHERE owner_user_id = #{ownerUserId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AgentAssetEntity> pageOwned(@Param("ownerUserId") String ownerUserId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM agent_asset WHERE owner_user_id = #{ownerUserId} AND session_id = #{sessionId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AgentAssetEntity> pageOwnedInSession(@Param("ownerUserId") String ownerUserId, @Param("sessionId") String sessionId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM agent_asset WHERE asset_id = #{assetId} AND owner_user_id = #{ownerUserId}")
    AgentAssetEntity findOwned(@Param("ownerUserId") String ownerUserId, @Param("assetId") String assetId);

    @Delete("DELETE FROM agent_asset WHERE asset_id = #{assetId} AND owner_user_id = #{ownerUserId}")
    int deleteOwned(@Param("ownerUserId") String ownerUserId, @Param("assetId") String assetId);
}