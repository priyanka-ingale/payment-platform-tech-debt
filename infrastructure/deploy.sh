#!/bin/bash

###############################################################################
# Payment Platform Deployment Script
# TECH DEBT: 800 lines of bash spaghetti with no error handling
# Last modified: 2019
# Author: John Doe (left company in 2021)
###############################################################################

# TECH DEBT: No set -e, script continues even if commands fail
# TECH DEBT: No set -u, undefined variables silently treated as empty strings

echo "=== Starting Payment Platform Deployment ==="
echo "WARNING: This script takes 45 minutes to run"
echo "WARNING: Do not interrupt or services may be left in inconsistent state"

# TECH DEBT: Hardcoded server IPs (should use service discovery)
GATEWAY_SERVERS="10.0.1.10 10.0.1.11 10.0.1.12"
SUBSCRIPTION_SERVERS="10.0.2.10 10.0.2.11"
BILLING_SERVERS="10.0.3.10 10.0.3.11 10.0.3.12 10.0.3.13"

# TECH DEBT: Database connection string with credentials
DB_HOST="prod-db.internal.company.com"
DB_USER="deploy_user"
DB_PASS="DeployP@ss2019!"

# TECH DEBT: Asks for user input mid-deployment (blocks automation)
echo "Deploy to production? (yes/no)"
read CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Deployment cancelled"
    exit 1
fi

# TECH DEBT: No version checking, might deploy incompatible versions
echo "Building payment-gateway..."
cd services/payment-gateway
mvn clean package

# TECH DEBT: No check if build succeeded
echo "Building subscription-service..."
cd ../subscription-service
mvn clean package

echo "Building billing-service..."
cd ../billing-service
mvn clean package

cd ../../

# TECH DEBT: Stop all services simultaneously (causes 100% downtime)
echo "Stopping all payment gateway servers..."
for server in $GATEWAY_SERVERS; do
    ssh deploy@$server "systemctl stop payment-gateway"
done

echo "Stopping all subscription servers..."
for server in $SUBSCRIPTION_SERVERS; do
    ssh deploy@$server "systemctl stop subscription-service"
done

echo "Stopping all billing servers..."
for server in $BILLING_SERVERS; do
    ssh deploy@$server "systemctl stop billing-service"
done

# TECH DEBT: All services down, platform is completely offline
echo "Platform is now OFFLINE"
echo "Sleeping 10 seconds..."
sleep 10

# TECH DEBT: Database migrations run while services are down
# But if migration fails, services won't start and we're stuck
echo "Running database migrations..."
PGPASSWORD=$DB_PASS psql -h $DB_HOST -U $DB_USER -d payments << EOF
-- TECH DEBT: No transaction wrapping, if one fails, DB is inconsistent

ALTER TABLE payments ADD COLUMN IF NOT EXISTS new_field_1 VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS new_field_2 INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS new_field_3 TIMESTAMP;

-- TECH DEBT: Potentially slow operation on production table with millions of rows
UPDATE payments SET new_field_1 = 'default' WHERE new_field_1 IS NULL;

-- TECH DEBT: No rollback plan if this fails
CREATE INDEX IF NOT EXISTS idx_payments_new ON payments(new_field_1);

-- More migrations...
EOF

# TECH DEBT: No check if migrations succeeded
echo "Migrations complete (hopefully)"

# TECH DEBT: Copy JARs one by one, no parallel deployment
echo "Deploying payment-gateway..."
for server in $GATEWAY_SERVERS; do
    echo "Deploying to $server..."
    scp services/payment-gateway/target/payment-gateway.jar deploy@$server:/opt/payment-platform/
    
    # TECH DEBT: SSH commands can fail silently
    ssh deploy@$server << 'REMOTE_SCRIPT'
        cd /opt/payment-platform
        
        # TECH DEBT: No backup of old JAR
        rm -f payment-gateway-old.jar
        mv payment-gateway.jar payment-gateway-old.jar 2>/dev/null
        
        # TECH DEBT: Copy over hardcoded config
        cp /home/deploy/prod-config.json /opt/payment-platform/config/
        
        # TECH DEBT: No health check before starting
        systemctl start payment-gateway
        
        # TECH DEBT: Sleep instead of waiting for actual readiness
        sleep 15
REMOTE_SCRIPT
    
    echo "Deployed to $server (maybe)"
done

# TECH DEBT: Same deployment process copy-pasted for each service
echo "Deploying subscription-service..."
for server in $SUBSCRIPTION_SERVERS; do
    echo "Deploying to $server..."
    scp services/subscription-service/target/subscription-service.jar deploy@$server:/opt/payment-platform/
    
    ssh deploy@$server << 'REMOTE_SCRIPT'
        cd /opt/payment-platform
        rm -f subscription-service-old.jar
        mv subscription-service.jar subscription-service-old.jar 2>/dev/null
        cp /home/deploy/prod-config.json /opt/payment-platform/config/
        systemctl start subscription-service
        sleep 15
REMOTE_SCRIPT
    
    echo "Deployed to $server (maybe)"
done

echo "Deploying billing-service..."
for server in $BILLING_SERVERS; do
    echo "Deploying to $server..."
    scp services/billing-service/target/billing-service.jar deploy@$server:/opt/payment-platform/
    
    ssh deploy@$server << 'REMOTE_SCRIPT'
        cd /opt/payment-platform
        rm -f billing-service-old.jar
        mv billing-service.jar billing-service-old.jar 2>/dev/null
        cp /home/deploy/prod-config.json /opt/payment-platform/config/
        systemctl start billing-service
        sleep 15
REMOTE_SCRIPT
    
    echo "Deployed to $server (maybe)"
done

# TECH DEBT: "Smoke test" is just curling one endpoint
echo "Running smoke tests..."
curl -s http://10.0.1.10:8080/health
if [ $? -eq 0 ]; then
    echo "Smoke test passed!"
else
    echo "Smoke test failed, but deployment is complete anyway"
    # TECH DEBT: No automatic rollback
fi

# TECH DEBT: Clear cache manually on each Redis instance
echo "Clearing Redis cache..."
redis-cli -h redis-1.internal FLUSHALL
redis-cli -h redis-2.internal FLUSHALL
redis-cli -h redis-3.internal FLUSHALL

# TECH DEBT: Restart load balancer to pick up new servers
echo "Restarting load balancer..."
ssh deploy@lb-1.internal "systemctl restart nginx"
ssh deploy@lb-2.internal "systemctl restart nginx"

# TECH DEBT: Total deployment time is 45 minutes due to sequential operations
echo "=== Deployment Complete ==="
echo "Total downtime: ~45 minutes"
echo "Platform should be online now (check manually)"
echo "If anything broke, good luck debugging!"
echo ""
echo "NOTE: John Doe (original author) left in 2021"
echo "      No one fully understands this script"
echo "      Modify at your own risk"
