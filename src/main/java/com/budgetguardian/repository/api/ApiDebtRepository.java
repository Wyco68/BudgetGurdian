package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.dto.DebtDto;
import com.budgetguardian.dto.DebtPaymentDto;
import com.budgetguardian.dto.DebtStatusDto;
import com.budgetguardian.mapper.DebtMapper;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.DebtRepository;
import com.budgetguardian.repository.StorageException;

/** REST access to {@code /debts} and {@code /debt-payments}. */
public final class ApiDebtRepository implements DebtRepository {

    private final HttpJsonClient http;

    public ApiDebtRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public HashMap<Long, Debt> findAll() throws StorageException {
        try {
            DebtDto[] dtos = http.get("/debts", DebtDto[].class);
            HashMap<Long, Debt> debts = new HashMap<>();
            for (DebtDto dto : dtos) {
                Debt debt = DebtMapper.toModel(dto);
                debts.put(debt.id(), debt);
            }
            return debts;
        } catch (ApiException e) {
            throw new StorageException("Failed to load debts from backend", e);
        }
    }

    @Override
    public DoublyLinkedList<DebtPayment> findAllPayments() throws StorageException {
        try {
            DebtPaymentDto[] dtos = http.get("/debt-payments", DebtPaymentDto[].class);
            DoublyLinkedList<DebtPayment> payments = new DoublyLinkedList<>();
            for (DebtPaymentDto dto : dtos) {
                payments.addLast(DebtMapper.toModel(dto));
            }
            return payments;
        } catch (ApiException e) {
            throw new StorageException("Failed to load debt payments from backend", e);
        }
    }

    @Override
    public Debt insert(Debt debt) throws StorageException {
        try {
            DebtDto saved = http.post("/debts", DebtMapper.toDto(debt), DebtDto.class);
            return debt.withId(saved.id);
        } catch (ApiException e) {
            throw new StorageException("Failed to save debt", e);
        }
    }

    @Override
    public void updateStatus(Debt debt) throws StorageException {
        try {
            String settledDate = debt.settledDate() != null ? debt.settledDate().toString() : null;
            http.put("/debts/" + debt.id() + "/status",
                    new DebtStatusDto(debt.status().name(), settledDate), DebtDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to update debt " + debt.id(), e);
        }
    }

    @Override
    public void delete(long debtId) throws StorageException {
        try {
            http.delete("/debts/" + debtId);
        } catch (ApiException e) {
            throw new StorageException("Failed to delete debt " + debtId, e);
        }
    }

    @Override
    public DebtPayment insertPayment(DebtPayment payment) throws StorageException {
        try {
            DebtPaymentDto saved = http.post("/debts/" + payment.debtId() + "/payments",
                    DebtMapper.toDto(payment), DebtPaymentDto.class);
            return payment.withId(saved.id);
        } catch (ApiException e) {
            throw new StorageException("Failed to save debt payment", e);
        }
    }

    @Override
    public void deletePayment(long paymentId) throws StorageException {
        try {
            http.delete("/debt-payments/" + paymentId);
        } catch (ApiException e) {
            throw new StorageException("Failed to delete payment " + paymentId, e);
        }
    }
}
