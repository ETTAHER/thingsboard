/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.dao.model.ModelConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public abstract class BaseCustomerControllerTest extends AbstractControllerTest {
    static final TypeReference<PageData<Customer>> PAGE_DATA_CUSTOMER_TYPE_REFERENCE = new TypeReference<>() {
    };

    ListeningExecutorService executor;

    private Tenant savedTenant;
    private User tenantAdmin;


    @SpyBean
    private TbClusterService tbClusterService;

    @SpyBean
    private AuditLogService auditLogService;
    ;

    @Before
    public void beforeTest() throws Exception {
        executor = MoreExecutors.listeningDecorator(ThingsBoardExecutors.newWorkStealingPool(8, getClass()));

        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
    }

    @After
    public void afterTest() throws Exception {
        executor.shutdownNow();

        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");

        Mockito.reset(tbClusterService, auditLogService);

        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedCustomer.getTenantId()),
                Mockito.eq(tenantAdmin.getCustomerId()), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(savedCustomer.getId()), Mockito.eq(savedCustomer), Mockito.eq(ActionType.ADDED), Mockito.isNull());
        Mockito.verify(tbClusterService, times(1)).pushMsgToRuleEngine(Mockito.eq(savedTenant.getId()),
                Mockito.eq(savedCustomer.getId()), Mockito.any(TbMsg.class), Mockito.isNull());

        Assert.assertNotNull(savedCustomer);
        Assert.assertNotNull(savedCustomer.getId());
        Assert.assertTrue(savedCustomer.getCreatedTime() > 0);
        Assert.assertEquals(customer.getTitle(), savedCustomer.getTitle());
        savedCustomer.setTitle("My new customer");

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", savedCustomer, Customer.class);

        Mockito.verify(tbClusterService, times(1)).sendNotificationMsgToEdgeService(Mockito.eq(savedCustomer.getTenantId()),
                Mockito.isNull(), Mockito.eq(savedCustomer.getId()), Mockito.isNull(), Mockito.isNull(), Mockito.eq(EdgeEventActionType.UPDATED));
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedCustomer.getTenantId()),
                Mockito.eq(savedCustomer.getId()), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(savedCustomer.getId()), Mockito.eq(savedCustomer), Mockito.eq(ActionType.UPDATED), Mockito.isNull());
        Mockito.verify(tbClusterService, times(1)).pushMsgToRuleEngine(Mockito.eq(savedTenant.getId()),
                Mockito.eq(savedCustomer.getId()), Mockito.any(TbMsg.class), Mockito.isNull());

        Customer foundCustomer = doGet("/api/customer/" + savedCustomer.getId().getId().toString(), Customer.class);
        Assert.assertEquals(foundCustomer.getTitle(), savedCustomer.getTitle());

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveCustomerWithViolationOfValidation() throws Exception {
        Customer customer = new Customer();
        customer.setTitle(RandomStringUtils.randomAlphabetic(300));

        CustomerId customer_NULL_UUID = (CustomerId) EntityIdFactory.getByTypeAndUuid(EntityType.CUSTOMER, ModelConstants.NULL_UUID);
        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", customer).andExpect(statusReason(containsString("length of title must be equal or less than 255")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
        Mockito.reset(tbClusterService, auditLogService);

        customer.setTitle("Normal title");
        customer.setCity(RandomStringUtils.randomAlphabetic(300));
        doPost("/api/customer", customer).andExpect(statusReason(containsString("length of city must be equal or less than 255")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
        Mockito.reset(tbClusterService, auditLogService);

        customer.setCity("Normal city");
        customer.setCountry(RandomStringUtils.randomAlphabetic(300));
        doPost("/api/customer", customer).andExpect(statusReason(containsString("length of country must be equal or less than 255")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
        Mockito.reset(tbClusterService, auditLogService);

        customer.setCountry("Ukraine");
        customer.setPhone(RandomStringUtils.randomAlphabetic(300));
        doPost("/api/customer", customer).andExpect(statusReason(containsString("length of phone must be equal or less than 255")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
        Mockito.reset(tbClusterService, auditLogService);

        customer.setPhone("+3892555554512");
        customer.setState(RandomStringUtils.randomAlphabetic(300));
        doPost("/api/customer", customer).andExpect(statusReason(containsString("length of state must be equal or less than 255")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
        Mockito.reset(tbClusterService, auditLogService);

        customer.setState("Normal state");
        customer.setZip(RandomStringUtils.randomAlphabetic(300));

        doPost("/api/customer", customer).andExpect(statusReason(containsString("length of zip or postal code must be equal or less than 255")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
        Mockito.reset(tbClusterService, auditLogService);

        customer.setZip("Normal zip");
        customer.setEmail("invalid@mail");
        doPost("/api/customer", customer)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Invalid email address format 'invalid@mail'")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());

    }

    @Test
    public void testUpdateCustomerFromDifferentTenant() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        doPost("/api/customer", savedCustomer, Customer.class);

        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", savedCustomer, Customer.class, status().isForbidden());

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, never()).logEntityAction(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(Customer.class), Mockito.any(),
                Mockito.any());
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());

        deleteDifferentTenant();

        login(tenantAdmin.getName(), "testPassword1");
        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testFindCustomerById() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Customer foundCustomer = doGet("/api/customer/" + savedCustomer.getId().getId().toString(), Customer.class);
        Assert.assertNotNull(foundCustomer);
        Assert.assertEquals(savedCustomer, foundCustomer);

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testDeleteCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedCustomer.getTenantId()),
                Mockito.eq(savedCustomer.getId()), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(savedCustomer.getId()), Mockito.eq(savedCustomer), Mockito.eq(ActionType.DELETED), Mockito.isNull(), Mockito.eq(savedCustomer.getId().toString()));
        Mockito.verify(tbClusterService, times(1)).pushMsgToRuleEngine(Mockito.eq(savedTenant.getId()),
                Mockito.eq(savedCustomer.getId()), Mockito.any(TbMsg.class), Mockito.isNull());
        Mockito.verify(tbClusterService, times(1)).
                broadcastEntityStateChangeEvent(Mockito.eq(savedCustomer.getTenantId()),
                Mockito.eq(savedCustomer.getId()), Mockito.eq(ComponentLifecycleEvent.DELETED));

        doGet("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testSaveCustomerWithEmptyTitle() throws Exception {
        Customer customer = new Customer();

        CustomerId customer_NULL_UUID = (CustomerId) EntityIdFactory.getByTypeAndUuid(EntityType.CUSTOMER, ModelConstants.NULL_UUID);
        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", customer)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Customer title should be specified")));

        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdgeService(Mockito.any(),
                Mockito.any(), Mockito.any(CustomerId.class), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(auditLogService, times(1)).logEntityAction(Mockito.eq(savedTenant.getId()),
                Mockito.eq(customer_NULL_UUID), Mockito.eq(tenantAdmin.getId()), Mockito.eq(tenantAdmin.getEmail()),
                Mockito.eq(customer_NULL_UUID), Mockito.any(Customer.class),Mockito.eq(ActionType.ADDED),
                Mockito.any(org.thingsboard.server.dao.exception.DataValidationException.class));
        Mockito.verify(tbClusterService, never()).pushMsgToRuleEngine(Mockito.any(), Mockito.any(CustomerId.class),
                Mockito.any(), Mockito.any());
    }

    @Test
    public void testFindCustomers() throws Exception {
        TenantId tenantId = savedTenant.getId();

        List<ListenableFuture<Customer>> futures = new ArrayList<>(135);
        for (int i = 0; i < 135; i++) {
            Customer customer = new Customer();
            customer.setTenantId(tenantId);
            customer.setTitle("Customer" + i);
            futures.add(executor.submit(() ->
                    doPost("/api/customer", customer, Customer.class)));
        }
        List<Customer> customers = Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS);

        List<Customer> loadedCustomers = new ArrayList<>(135);
        PageLink pageLink = new PageLink(23);
        PageData<Customer> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
            loadedCustomers.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(customers).containsExactlyInAnyOrderElementsOf(loadedCustomers);

        deleteEntitiesAsync("/api/customer/", loadedCustomers, executor).get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void testFindCustomersByTitle() throws Exception {
        TenantId tenantId = savedTenant.getId();

        String title1 = "Customer title 1";
        List<ListenableFuture<Customer>> futures = new ArrayList<>(143);
        for (int i = 0; i < 143; i++) {
            Customer customer = new Customer();
            customer.setTenantId(tenantId);
            String suffix = RandomStringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String title = title1 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            customer.setTitle(title);
            futures.add(executor.submit(() ->
                    doPost("/api/customer", customer, Customer.class)));
        }
        List<Customer> customersTitle1 = Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS);

        String title2 = "Customer title 2";
        futures = new ArrayList<>(175);
        for (int i = 0; i < 175; i++) {
            Customer customer = new Customer();
            customer.setTenantId(tenantId);
            String suffix = RandomStringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String title = title2 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            customer.setTitle(title);
            futures.add(executor.submit(() ->
                    doPost("/api/customer", customer, Customer.class)));
        }

        List<Customer> customersTitle2 = Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS);

        List<Customer> loadedCustomersTitle1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Customer> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
            loadedCustomersTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(customersTitle1).as(title1).containsExactlyInAnyOrderElementsOf(loadedCustomersTitle1);

        List<Customer> loadedCustomersTitle2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
            loadedCustomersTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(customersTitle2).as(title2).containsExactlyInAnyOrderElementsOf(loadedCustomersTitle2);

        deleteEntitiesAsync("/api/customer/", loadedCustomersTitle1, executor).get(TIMEOUT, TimeUnit.SECONDS);

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        deleteEntitiesAsync("/api/customer/", loadedCustomersTitle2, executor).get(TIMEOUT, TimeUnit.SECONDS);

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

}
