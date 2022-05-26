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
package org.thingsboard.server.service.sync.vc;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.sync.vc.EntitiesVersionControlSettings;
import org.thingsboard.server.common.data.sync.vc.EntityVersion;
import org.thingsboard.server.common.data.sync.vc.VersionCreationResult;
import org.thingsboard.server.common.data.sync.vc.VersionedEntityInfo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface GitRepositoryService {

    Set<TenantId> getActiveRepositoryTenants();

    void prepareCommit(PendingCommit pendingCommit);

    PageData<EntityVersion> listVersions(TenantId tenantId, String branch, String path, PageLink pageLink) throws Exception;

    List<VersionedEntityInfo> listEntitiesAtVersion(TenantId tenantId, String versionId, String path) throws Exception;

    void testRepository(TenantId tenantId, EntitiesVersionControlSettings settings) throws Exception;

    void initRepository(TenantId tenantId, EntitiesVersionControlSettings settings) throws Exception;

    EntitiesVersionControlSettings getRepositorySettings(TenantId tenantId) throws Exception;

    void clearRepository(TenantId tenantId) throws IOException;

    void add(PendingCommit commit, String relativePath, String entityDataJson) throws IOException;

    void deleteFolderContent(PendingCommit commit, String relativePath) throws IOException;

    VersionCreationResult push(PendingCommit commit);

    void cleanUp(PendingCommit commit);

    void abort(PendingCommit commit);

    List<String> listBranches(TenantId tenantId);

    String getFileContentAtCommit(TenantId tenantId, String relativePath, String versionId) throws IOException;

    void fetch(TenantId tenantId) throws GitAPIException;
}