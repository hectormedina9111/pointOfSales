package com.medinasystems.pos.inventory.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.domain.InventoryDocument;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.security.UserInfo;

/**
 * Names that the views show next to ids, looked up once per request. The other modules are asked through their public
 * APIs; this class never reads their tables. Unknown ids give null.
 */
final class InventoryNames {

    private final BranchDirectory branches;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryDocumentRepository documents;

    private final Map<Long, Optional<BranchInfo>> branchCache = new HashMap<>();
    private final Map<Long, Optional<UserInfo>> userCache = new HashMap<>();
    private final Map<Long, Optional<SaleableProduct>> productCache = new HashMap<>();
    private final Map<Long, Optional<String>> numberCache = new HashMap<>();

    InventoryNames(BranchDirectory branches, UserDirectory users, SaleableProducts products,
                   InventoryDocumentRepository documents) {
        this.branches = branches;
        this.users = users;
        this.products = products;
        this.documents = documents;
    }

    Optional<BranchInfo> branch(long branchId) {
        return branchCache.computeIfAbsent(branchId, branches::info);
    }

    String branchName(long branchId) {
        return branch(branchId).map(BranchInfo::name).orElse(null);
    }

    String userName(long userId) {
        return userCache.computeIfAbsent(userId, users::find).map(UserInfo::fullName).orElse(null);
    }

    Optional<SaleableProduct> product(long productId) {
        return productCache.computeIfAbsent(productId, products::find);
    }

    String productSku(long productId) {
        return product(productId).map(SaleableProduct::sku).orElse(null);
    }

    String productName(long productId) {
        return product(productId).map(SaleableProduct::name).orElse(null);
    }

    String documentNumber(long documentId) {
        return numberCache.computeIfAbsent(documentId,
                id -> documents.findById(id).map(InventoryDocument::getNumber)).orElse(null);
    }
}
