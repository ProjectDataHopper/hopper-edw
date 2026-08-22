# hop-hsm-jdbc (thin client)

Single-jar, **zero-dependency** JDBC driver for free SQL against a Hop Source Model exposed by Hop Server.

Install **only this jar** in DBeaver, SQuirreL, or other JDBC tools. No Hop, Calcite, or servlet libraries required.

## Build

```bash
mvn -f hop-hsm-jdbc/pom.xml clean package
# → hop-hsm-jdbc/target/hop-hsm-jdbc-*-SNAPSHOT.jar
```

## Server setup

1. Install the **Data Hopper EDW** (`hopper-edw`) plugin on Hop Server (includes the `/hop/sourceModelData` servlet).
2. In the Hop Server project metadata, create one or more **Source model service** entries (**Servers and Web Services**):
   - **Name** — becomes the **JDBC schema** name in DBeaver
   - **Source model file** — server-side VFS path to the `.hsm` (never exposed to clients)
   - **Enabled**, row limits, optional schema metadata
3. Ensure RDBMS connection metadata used by each model is available to the server.

Product docs with screenshots: [`docs/source-modeler-overview.adoc`](../docs/source-modeler-overview.adoc) (JDBC section).

## Hop GUI (built-in connection type)

With the **Data Hopper EDW** plugin installed, create a relational connection of type **Apache Hop Source Model**:

| Hop field | Maps to |
|-----------|---------|
| Host name | Hop Server host |
| Port | Hop Server HTTP port (default **8080**) |
| Database name | Optional default **Source model service** (JDBC schema) |
| Username / password | Hop Server credentials |

JDBC URL shape: `jdbc:hop-hsm://{host}:{port}/{service}`. Extra options use `?` / `&` (e.g. `rowLimit`, `connectTimeout`, `readTimeout`). The driver jar is bundled under `plugins/misc/datavault/lib/`.

## DBeaver setup

### Driver Manager

**Database → Driver Manager → New** (Generic):

| Setting | Value |
|---------|--------|
| Driver Name | e.g. `Apache Hop Source Model` |
| Class Name | `org.apache.hop.hsm.jdbc.HopHsmJdbcDriver` |
| URL Template | `jdbc:hop-hsm://{username}:{password}@{host}:{port}/{database}` |
| Libraries | Add `hop-hsm-jdbc-*-SNAPSHOT.jar` only |

`{database}` in the template is the default **schema** (Source model service name), not a physical database.

Screenshots in the repo:

* `docs/images/hop-source-model-jdbc-dbeaver-database-driver-manager.png`
* `docs/images/hop-source-model-jdbc-dbeaver-database-connection.png`
* `docs/images/hop-source-model-jdbc-dbeaver-query-editor.png`
* `docs/images/hop-source-model-service-editor.png` (Hop side)

### Connection

| Field | Meaning |
|-------|---------|
| Host / Port | Hop Server |
| Database/Schema | Source model service name (e.g. `crm`) |
| Username / Password | Hop Server basic auth |

Example resolved URL:

```
jdbc:hop-hsm://cluster:secret@localhost:8888/crm
```

Or without a default schema (browse all services):

```
jdbc:hop-hsm://cluster:secret@localhost:8888
```

### Querying

1. Navigator: schema = Source model service; tables/views = model objects.
2. Open SQL Editor with that schema active.
3. Run free SQL (bare or qualified names):

```sql
SELECT *
FROM crm.order_header oh
WHERE oh.total_amount > 1000
ORDER BY oh.total_amount desc
```

After jar/plugin upgrades, **Invalidate/Reconnect** in DBeaver.

## JDBC URL reference

```
jdbc:hop-hsm://user:pass@hop-server:8182
jdbc:hop-hsm://user:pass@hop-server:8182/crm
jdbc:hop-hsm://user:pass@hop-server:8182?schema=crm
jdbc:hop-hsm:https://user:pass@host:8443?schema=crm&rowLimit=5000
```

DBeaver Driver Manager template:

```
jdbc:hop-hsm://{username}:{password}@{host}:{port}/{database}
```

| Part | Meaning |
|------|---------|
| `user:pass@` | Hop Server basic auth |
| `host:port` | Hop Server |
| `/{database}` or `?schema=` | Default **schema** = Source model service name |
| Servlet path | Defaults to `/hop/sourceModelData` |

Driver class (ServiceLoader): `org.apache.hop.hsm.jdbc.HopHsmJdbcDriver`

**JDBC model**

| JDBC concept | Hop concept |
|--------------|-------------|
| Schema | Source model service metadata name |
| Table / View | Source table, named query, JSON, or pipeline feed |
| Connection | Hop Server (+ optional default schema) |

## Usage (programmatic)

```java
try (Connection c = DriverManager.getConnection(
        "jdbc:hop-hsm://cluster:secret@localhost:8182/crm");
     Statement st = c.createStatement();
     ResultSet rs = st.executeQuery(
         "SELECT customer_id, name FROM customer_hub WHERE customer_id > 0 LIMIT 100")) {
  while (rs.next()) {
    System.out.println(rs.getLong(1) + " " + rs.getString(2));
  }
}
```

Legacy `?modelName=crm` still works as an alias for `schema`.

## Protocol

POST form fields to `/hop/sourceModelData`: `schema` (or legacy `modelName`), `action`
(`schemas`|`tables`|`columns`|`query`|`ping`), `sql`, `rowLimit`, `table`.  
`action=schemas` lists all enabled Source model services (no schema required).
