package com.aircargo.authservice.config;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.CommodityTypeEntity;
import com.aircargo.authservice.entity.RolePermission;
import com.aircargo.authservice.entity.Site;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.entity.ViewPermission;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.AirlineRepository;
import com.aircargo.authservice.repository.CommodityTypeRepository;
import com.aircargo.authservice.repository.RolePermissionRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.authservice.repository.ViewPermissionRepository;
import com.aircargo.common.entity.Airline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds master data (airline, sites, users, site assignments, view/role permissions)
 * on startup so a fresh database is immediately usable.
 *
 * Idempotent: existing rows are never duplicated or overwritten.
 * Disabled under the "test" profile (H2 integration tests seed their own data).
 */
@Component
@Profile("!test")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final UUID UPS_AIRLINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SDQ_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STI_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID PUJ_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID MIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");

    private final AirlineRepository airlineRepository;
    private final SiteRepository siteRepository;
    private final AppUserRepository appUserRepository;
    private final ViewPermissionRepository viewPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CommodityTypeRepository commodityTypeRepository;

    public DataSeeder(AirlineRepository airlineRepository,
                      SiteRepository siteRepository,
                      AppUserRepository appUserRepository,
                      ViewPermissionRepository viewPermissionRepository,
                      RolePermissionRepository rolePermissionRepository,
                      CommodityTypeRepository commodityTypeRepository) {
        this.airlineRepository = airlineRepository;
        this.siteRepository = siteRepository;
        this.appUserRepository = appUserRepository;
        this.viewPermissionRepository = viewPermissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.commodityTypeRepository = commodityTypeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Airline ups = seedUpsAirline();
        List<Site> sites = seedSites();
        Site sdq = sites.stream().filter(s -> "SDQ".equals(s.getCode())).findFirst().orElse(sites.get(0));
        seedUsers(ups, sdq);
        seedViewPermissions();
        seedRolePermissions();
        seedCommodityTypes();
        log.info("DataSeeder: master data verified (airline, sites, users, permissions, commodity types)");
    }

    private Airline seedUpsAirline() {
        return airlineRepository.findByCode("UPS").orElseGet(() -> {
            Airline ups = Airline.builder()
                    .id(UPS_AIRLINE_ID)
                    .code("UPS")
                    .name("United Parcel Service")
                    .iataCode("5X")
                    .country("USA")
                    .isActive(true)
                    .build();
            Airline saved = airlineRepository.save(ups);
            log.info("DataSeeder: seeded airline {}", saved.getCode());
            return saved;
        });
    }

    private List<Site> seedSites() {
        List<Site> existing = siteRepository.findAll();
        if (existing.size() >= 4) {
            return existing;
        }
        seedSite(SDQ_ID, "SDQ", "Santo Domingo", "DO");
        seedSite(STI_ID, "STI", "Santiago", "DO");
        seedSite(PUJ_ID, "PUJ", "Punta Cana", "DO");
        seedSite(MIA_ID, "MIA", "Miami", "US");
        return siteRepository.findAll();
    }

    private void seedSite(UUID id, String code, String name, String country) {
        if (siteRepository.existsByCode(code)) {
            return;
        }
        Site site = Site.builder()
                .id(id)
                .code(code)
                .name(name)
                .country(country)
                .isActive(true)
                .build();
        siteRepository.save(site);
        log.info("DataSeeder: seeded site {}", code);
    }

    private void seedUsers(Airline ups, Site sdq) {
        Map<String, UserRole> users = new LinkedHashMap<>();
        users.put("readonly@aircargo.com", UserRole.READ_ONLY);
        users.put("warehouse@aircargo.com", UserRole.WAREHOUSE_ASSISTANT);
        users.put("operations@aircargo.com", UserRole.OPERATIONS);
        users.put("traffic@aircargo.com", UserRole.TRAFFIC);
        users.put("loadplanner@aircargo.com", UserRole.LOAD_PLANNER);
        users.put("admin@aircargo.com", UserRole.ADMIN);
        users.put("supervisor@aircargo.com", UserRole.SUPER_USER);
        users.put("jsantos@rannik.com", UserRole.ADMIN);
        users.put("esantana@rannik.com", UserRole.SUPER_USER);
        users.put("dchestaro@rannik.com", UserRole.OPERATIONS);
        users.put("ilsantana@rannik.com", UserRole.WAREHOUSE_ASSISTANT);
        users.put("earellano@ups.com", UserRole.TRAFFIC);
        users.put("jcastrolopez@ups.com", UserRole.LOAD_PLANNER);
        users.put("bi@rannik.com", UserRole.BI_USER);

        users.forEach((email, role) -> {
            if (appUserRepository.existsByEmail(email)) {
                return;
            }
            String fullName = fullNameFor(email, role);
            AppUser user = AppUser.builder()
                    .airline(ups)
                    .email(email)
                    .fullName(fullName)
                    .role(role)
                    .passwordHash(null)
                    .mfaEnabled(false)
                    .mfaLocked(false)
                    .mustChangePassword(false)
                    .isActive(true)
                    .failedLoginAttempts(0)
                    .sites(new LinkedHashSet<>(Set.of(sdq)))
                    .build();
            appUserRepository.save(user);
            log.info("DataSeeder: seeded user {} ({})", email, role);
        });
    }

    private String fullNameFor(String email, UserRole role) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("readonly@aircargo.com", "Read Only User");
        names.put("warehouse@aircargo.com", "Warehouse Assistant");
        names.put("operations@aircargo.com", "Operations User");
        names.put("traffic@aircargo.com", "Traffic User");
        names.put("loadplanner@aircargo.com", "Load Planner");
        names.put("admin@aircargo.com", "Admin User");
        names.put("supervisor@aircargo.com", "Supervisor");
        names.put("jsantos@rannik.com", "Jose Santos");
        names.put("esantana@rannik.com", "Edward Santana");
        names.put("dchestaro@rannik.com", "Danny Chestaro");
        names.put("ilsantana@rannik.com", "Ilsa Santana");
        names.put("earellano@ups.com", "Eduardo Arellano");
        names.put("jcastrolopez@ups.com", "Jairo Castro");
        names.put("bi@rannik.com", "BI User");
        return names.getOrDefault(email, role.name());
    }

    private void seedViewPermissions() {
        if (viewPermissionRepository.count() > 0) {
            return;
        }
        Map<String, String[]> views = new LinkedHashMap<>();
        views.put("DASHBOARD", new String[]{"Dashboard", "Panel principal con resumen de operaciones", "PRINCIPAL"});
        views.put("BOOKINGS", new String[]{"Bookings", "Gestión de reservas y bookings", "PRINCIPAL"});
        views.put("RECEIPTS", new String[]{"Recibos de Almacén", "Emisión y consulta de recibos de bodega", "PRINCIPAL"});
        views.put("FLIGHTS", new String[]{"Vuelos", "Administración de vuelos y programación", "PRINCIPAL"});
        views.put("MAWBS", new String[]{"MAWBs", "Gestión de conocimientos aéreos maestros", "PRINCIPAL"});
        views.put("LOAD_PLANNING", new String[]{"Load Planning", "Planificación de carga y distribución", "PRINCIPAL"});
        views.put("ULDS", new String[]{"ULDs / Pallet Sheets", "Administración de contenedores y pallets", "PRINCIPAL"});
        views.put("HAWBS", new String[]{"HAWBs", "Gestión de conocimientos aéreos hijos", "OPERACIONES"});
        views.put("AIRLINES", new String[]{"Aerolíneas", "Administración de líneas aéreas y compañías", "OPERACIONES"});
        views.put("RAMP_MANIFEST", new String[]{"Manifiesto de Rampa", "Manifiesto de carga para operaciones de rampa", "OPERACIONES"});
        views.put("DIM_FACTOR", new String[]{"Factor Dimensional", "Configuración del factor dimensional para cálculos", "CONFIGURACION"});
        views.put("ULD_TYPE_CONFIG", new String[]{"Tipos de ULD", "Configuración de tipos de contenedores y pallets", "CONFIGURACION"});
        views.put("USERS", new String[]{"Usuarios", "Gestión de usuarios del sistema", "ADMINISTRACION"});
        views.put("AUDIT_LOG", new String[]{"Auditoría", "Consulta de bitácora de transacciones del sistema", "ADMINISTRACION"});
        views.put("ROLES", new String[]{"Roles y Permisos", "Administración de roles y permisos de acceso", "ADMINISTRACION"});
        views.put("SITES", new String[]{"Sitios / Aeropuertos", "Administración de códigos de sitio y aeropuertos", "ADMINISTRACION"});
        views.put("SETTINGS", new String[]{"Configuración Global", "Configuración general del sistema y parámetros", "ADMINISTRACION"});
        views.put("REPORTS", new String[]{"Reportes", "Generación y exportación de reportes operativos", "OPERACIONES"});
        views.put("EXPORTS", new String[]{"Exports", "Exportaciones y reportes", "ADMINISTRACION"});
        views.put("API_CATALOG", new String[]{"API Catalog", "Catálogo de endpoints de la API", "CONFIGURACION"});
        views.put("BI", new String[]{"BI", "Indicadores e inteligencia de negocio", "ADMINISTRACION"});

        views.forEach((code, data) -> {
            ViewPermission vp = ViewPermission.builder()
                    .code(code)
                    .name(data[0])
                    .description(data[1])
                    .category(data[2])
                    .isActive(true)
                    .build();
            viewPermissionRepository.save(vp);
        });
        log.info("DataSeeder: seeded {} view permissions", views.size());
    }

    private void seedRolePermissions() {
        if (rolePermissionRepository.count() > 0) {
            return;
        }
        Map<String, Set<String>> assignments = roleAssignments();

        assignments.forEach((role, codes) -> {
            codes.forEach(code -> {
                viewPermissionRepository.findByCode(code).ifPresent(vp -> {
                    RolePermission rp = RolePermission.builder()
                            .role(role)
                            .viewPermission(vp)
                            .canAccess(true)
                            .build();
                    rolePermissionRepository.save(rp);
                });
            });
        });
        log.info("DataSeeder: seeded role permissions for {} roles", assignments.size());
    }

    private Map<String, Set<String>> roleAssignments() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        Set<String> all = Set.of(
                "DASHBOARD", "BOOKINGS", "RECEIPTS", "FLIGHTS", "MAWBS", "LOAD_PLANNING",
                "ULDS", "HAWBS", "AIRLINES", "RAMP_MANIFEST", "DIM_FACTOR", "ULD_TYPE_CONFIG",
                "USERS", "AUDIT_LOG", "ROLES", "SITES", "SETTINGS", "REPORTS", "EXPORTS",
                "API_CATALOG", "BI");

        map.put("READ_ONLY", all);
        map.put("WAREHOUSE_ASSISTANT", Set.of("DASHBOARD", "RECEIPTS"));
        map.put("OPERATIONS", Set.of("DASHBOARD", "FLIGHTS", "MAWBS", "LOAD_PLANNING", "ULDS", "HAWBS", "AIRLINES", "RAMP_MANIFEST", "REPORTS"));
        map.put("TRAFFIC", Set.of("DASHBOARD", "BOOKINGS", "MAWBS", "LOAD_PLANNING", "ULDS", "HAWBS", "AIRLINES", "REPORTS"));
        map.put("LOAD_PLANNER", Set.of("DASHBOARD", "FLIGHTS", "LOAD_PLANNING", "ULDS", "ULD_TYPE_CONFIG", "REPORTS"));
        Set<String> admin = new LinkedHashSet<>(all);
        admin.remove("SETTINGS");
        map.put("ADMIN", admin);
        map.put("SUPER_USER", all);
        return map;
    }

    private void seedCommodityTypes() {
        if (commodityTypeRepository.count() > 0) {
            return;
        }
        record CommoditySeed(String code, String label, String color, int order) {}
        List<CommoditySeed> seeds = List.of(
            new CommoditySeed("PERISHABLE", "PERISHABLE", "#ef4444", 1),
            new CommoditySeed("DRY_CARGO", "DRY CARGO", "#64748b", 2),
            new CommoditySeed("ELECTRONICS", "ELECTRONICS", "#8b5cf6", 3),
            new CommoditySeed("HIGH_VALUES", "HIGH VALUES", "#f59e0b", 4),
            new CommoditySeed("CIGARETTES", "CIGARETTES", "#78716c", 5),
            new CommoditySeed("SMALL_PACKAGES", "SMALL PACKAGES", "#06b6d4", 6),
            new CommoditySeed("WWEF", "WWEF", "#ec4899", 7),
            new CommoditySeed("LIVE_PLANTS", "LIVE PLANTS", "#22c55e", 8),
            new CommoditySeed("GENERAL", "GENERAL", "#94a3b8", 9),
            new CommoditySeed("COMAT", "COMAT", "#a3a3a3", 10),
            new CommoditySeed("FCC", "FCC", "#78716c", 11),
            new CommoditySeed("EMPTY_ULD", "EMPTY ULD", "#d1d5db", 12),
            new CommoditySeed("EMPTY_PALLET", "EMPTY PALLET", "#d1d5db", 13),
            new CommoditySeed("RED_TAG", "RED TAG", "#dc2626", 14),
            new CommoditySeed("EMPTY_BAGS", "EMPTY BAGS", "#a3a3a3", 15),
            new CommoditySeed("NETS", "NETS", "#52525b", 16),
            new CommoditySeed("SDQ_SDF", "SDQ-SDF", "#2563eb", 17),
            new CommoditySeed("SDQ_MIA", "SDQ-MIA", "#2563eb", 18)
        );
        seeds.forEach(s -> {
            CommodityTypeEntity entity = CommodityTypeEntity.builder()
                .code(s.code())
                .label(s.label())
                .color(s.color())
                .sortOrder(s.order())
                .isActive(true)
                .build();
            commodityTypeRepository.save(entity);
        });
        log.info("DataSeeder: seeded {} commodity types", seeds.size());
    }
}
