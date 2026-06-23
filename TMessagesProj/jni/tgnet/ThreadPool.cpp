#include "ThreadPool.h"

ThreadPool::ThreadPool(size_t threads) : stop(false) {
    pthread_mutex_init(&queue_mutex, nullptr);
    pthread_cond_init(&condition, nullptr);

    for (size_t i = 0; i < threads; ++i) {
        pthread_t thread;
        pthread_create(&thread, nullptr, workerThread, this);
        workers.push_back(thread);
    }
}

ThreadPool::~ThreadPool() {
    pthread_mutex_lock(&queue_mutex);
    stop = true;
    pthread_cond_broadcast(&condition);
    pthread_mutex_unlock(&queue_mutex);

    for (pthread_t thread : workers) {
        pthread_join(thread, nullptr);
    }

    pthread_mutex_destroy(&queue_mutex);
    pthread_cond_destroy(&condition);
}

void ThreadPool::enqueue(std::function<void()> task) {
    pthread_mutex_lock(&queue_mutex);
    tasks.push(std::move(task));
    pthread_cond_signal(&condition);
    pthread_mutex_unlock(&queue_mutex);
}

void* ThreadPool::workerThread(void* arg) {
    ThreadPool* pool = static_cast<ThreadPool*>(arg);

    while (true) {
        std::function<void()> task;

        pthread_mutex_lock(&pool->queue_mutex);
        while (!pool->stop && pool->tasks.empty()) {
            pthread_cond_wait(&pool->condition, &pool->queue_mutex);
        }

        if (pool->stop && pool->tasks.empty()) {
            pthread_mutex_unlock(&pool->queue_mutex);
            break;
        }

        task = std::move(pool->tasks.front());
        pool->tasks.pop();
        pthread_mutex_unlock(&pool->queue_mutex);

        if (task) {
            task();
        }
    }

    return nullptr;
}
