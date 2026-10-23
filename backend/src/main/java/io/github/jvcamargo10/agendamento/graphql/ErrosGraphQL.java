package io.github.jvcamargo10.agendamento.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.github.jvcamargo10.agendamento.dominio.RegraDeNegocioException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/** Regras de negócio e validação viram erros BAD_REQUEST com mensagem legível; o resto fica genérico. */
@Component
public class ErrosGraphQL extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable erro, DataFetchingEnvironment ambiente) {
        if (erro instanceof RegraDeNegocioException) {
            return GraphqlErrorBuilder.newError(ambiente).errorType(ErrorType.BAD_REQUEST).message(erro.getMessage()).build();
        }
        if (erro instanceof ConstraintViolationException violacoes) {
            String mensagem = violacoes.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; "));
            return GraphqlErrorBuilder.newError(ambiente).errorType(ErrorType.BAD_REQUEST).message(mensagem).build();
        }
        return null;
    }
}
