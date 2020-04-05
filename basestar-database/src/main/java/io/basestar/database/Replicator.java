package io.basestar.database;

import io.basestar.auth.Caller;
import io.basestar.database.event.ObjectCreatedEvent;
import io.basestar.database.event.ObjectDeletedEvent;
import io.basestar.database.event.ObjectUpdatedEvent;
import io.basestar.database.options.CreateOptions;
import io.basestar.database.options.DeleteOptions;
import io.basestar.database.options.UpdateOptions;
import io.basestar.event.Event;
import io.basestar.event.Handler;
import io.basestar.event.Handlers;

import java.util.concurrent.CompletableFuture;

public class Replicator implements Handler<Event> {

    private static final Handlers<Replicator> HANDLERS = Handlers.<Replicator>builder()
            .on(ObjectCreatedEvent.class, Replicator::onCreated)
            .on(ObjectUpdatedEvent.class, Replicator::onUpdated)
            .on(ObjectDeletedEvent.class, Replicator::onDeleted)
            .build();

    private final Caller caller;

    private final Database target;

    public Replicator(final Caller caller, final Database target) {

        this.caller = caller;
        this.target = target;
    }

    @Override
    public CompletableFuture<?> handle(final Event event) {

        return HANDLERS.handle(this, event);
    }

    private CompletableFuture<?> onCreated(final ObjectCreatedEvent event) {

        final CreateOptions options = new CreateOptions();
        return target.create(caller, event.getSchema(), event.getId(), event.getAfter(), options);
    }

    private CompletableFuture<?> onUpdated(final ObjectUpdatedEvent event) {

        final UpdateOptions options = new UpdateOptions()
                .setVersion(event.getVersion())
                .setMode(UpdateOptions.Mode.REPLACE);
        return target.update(caller, event.getSchema(), event.getId(), event.getAfter(), options);
    }

    private CompletableFuture<?> onDeleted(final ObjectDeletedEvent event) {

        final DeleteOptions options = new DeleteOptions()
                .setVersion(event.getVersion());
        return target.delete(caller, event.getSchema(), event.getId(), options);
    }
}
