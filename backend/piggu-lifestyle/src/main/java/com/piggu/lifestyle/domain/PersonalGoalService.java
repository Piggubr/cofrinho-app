package com.piggu.lifestyle.domain;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.PersonalGoalRequest;
import com.piggu.lifestyle.api.dto.PersonalGoalResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Metas do mes de cada pessoa.
 *
 * <p>Sao privadas: nem o titular ve as de outra pessoa da familia. Meta de outra pessoa
 * responde como inexistente, para nao revelar nem que existe. A meta vale para o mes em
 * que foi criada; quando o mes vira ela sai da lista padrao, mas continua no banco.</p>
 */
@Service
public class PersonalGoalService {

    private final PersonalGoalRepository repositorio;

    public PersonalGoalService(PersonalGoalRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<PersonalGoalResponse> listar(YearMonth mes, UUID usuarioId) {
        return repositorio.findByUserIdAndMonthOrderByCreatedAtAsc(usuarioId, mes.atDay(1)).stream()
                .map(PersonalGoalResponse::de)
                .toList();
    }

    /** A meta nasce no mes corrente (Brasilia). */
    @Transactional
    public PersonalGoalResponse criar(PersonalGoalRequest pedido, UUID usuarioId) {
        PersonalGoal meta = new PersonalGoal(YearMonth.now(Meses.BRASILIA), Texto.limitar(pedido.titulo(), 80),
                pedido.alvo(), atual(pedido), usuarioId);
        return PersonalGoalResponse.de(repositorio.save(meta));
    }

    @Transactional
    public PersonalGoalResponse atualizar(UUID id, PersonalGoalRequest pedido, UUID usuarioId) {
        PersonalGoal meta = buscar(id, usuarioId);
        meta.aplicar(Texto.limitar(pedido.titulo(), 80), pedido.alvo(), atual(pedido));
        return PersonalGoalResponse.de(repositorio.save(meta));
    }

    @Transactional
    public void excluir(UUID id, UUID usuarioId) {
        repositorio.delete(buscar(id, usuarioId));
    }

    private PersonalGoal buscar(UUID id, UUID usuarioId) {
        return repositorio.findByIdAndUserId(id, usuarioId)
                .orElseThrow(() -> new NotFoundException("Meta nao encontrada."));
    }

    private static int atual(PersonalGoalRequest pedido) {
        return pedido.atual() == null ? 0 : pedido.atual();
    }
}
