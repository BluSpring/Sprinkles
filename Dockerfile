FROM gcr.io/distroless/java21-debian12

ENV APP_HOME=/app

COPY ./* $APP_HOME/

WORKDIR $APP_HOME

CMD ["Sprinkles-1.0.0-all.jar"]