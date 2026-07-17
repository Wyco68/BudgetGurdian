package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.dto.TransactionDto;
import com.budgetguardian.mapper.TransactionMapper;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.TransactionRepository;

/**
 * REST access to {@code /transactions}.
 *
 * <p>The backend returns the ledger ordered by date then id, so the
 * {@code DoublyLinkedList} is built by O(1) tail appends exactly as the
 * SQLite implementation does.</p>
 */
public final class ApiTransactionRepository implements TransactionRepository {

    private final HttpJsonClient http;

    public ApiTransactionRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public DoublyLinkedList<Transaction> findAll() throws StorageException {
        try {
            TransactionDto[] dtos = http.get("/transactions", TransactionDto[].class);
            DoublyLinkedList<Transaction> ledger = new DoublyLinkedList<>();
            for (TransactionDto dto : dtos) {
                ledger.addLast(TransactionMapper.toModel(dto));
            }
            return ledger;
        } catch (ApiException e) {
            throw new StorageException("Failed to load transactions from backend", e);
        }
    }

    @Override
    public Transaction insert(Transaction txn) throws StorageException {
        try {
            TransactionDto saved =
                    http.post("/transactions", TransactionMapper.toDto(txn), TransactionDto.class);
            return txn.withId(saved.id);
        } catch (ApiException e) {
            throw new StorageException("Failed to save transaction", e);
        }
    }

    @Override
    public void restore(Transaction txn) throws StorageException {
        try {
            http.post("/transactions/" + txn.id() + "/restore",
                    TransactionMapper.toDto(txn), TransactionDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to restore transaction " + txn.id(), e);
        }
    }

    @Override
    public void update(Transaction txn) throws StorageException {
        try {
            http.put("/transactions/" + txn.id(), TransactionMapper.toDto(txn), TransactionDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to update transaction " + txn.id(), e);
        }
    }

    @Override
    public void delete(long id) throws StorageException {
        try {
            http.delete("/transactions/" + id);
        } catch (ApiException e) {
            throw new StorageException("Failed to delete transaction " + id, e);
        }
    }
}
