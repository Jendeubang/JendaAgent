# Step 28 Addendum: MySQL Connector 5.1 Compatibility

The upstream project pins MySQL Connector/J `5.1.47`. With Spring Framework 6, `JdbcTemplate` can route Java `Timestamp` parameters through the driver's `setObject` implementation, which throws a `NullPointerException` for the `agent_asset` upsert path.

`AgentAssetMetadataStore` now uses explicit `PreparedStatement.setTimestamp(...)` calls for all asset metadata writes. This preserves the existing dependency set and prevents COS server-upload fallback from failing after the object was successfully stored.

Verification:

```powershell
mysql -uroot -p123456 -D jenda_agent -e "SELECT asset_id, owner_user_id, session_id, source, created_at FROM agent_asset ORDER BY created_at DESC;"
```
